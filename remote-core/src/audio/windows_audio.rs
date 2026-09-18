use std::{
    ptr,
    sync::mpsc::{self, RecvTimeoutError},
    thread,
    time::Duration,
};

use anyhow::{Context, Result};
use tokio::sync::watch;
use windows::{
    Win32::{
        Media::Audio::{
            AUDIO_VOLUME_NOTIFICATION_DATA,
            Endpoints::{
                IAudioEndpointVolume, IAudioEndpointVolumeCallback,
                IAudioEndpointVolumeCallback_Impl,
            },
            IMMDevice, IMMDeviceEnumerator, MMDeviceEnumerator, eMultimedia, eRender,
        },
        System::Com::{
            CLSCTX_ALL, COINIT_MULTITHREADED, CoCreateInstance, CoInitializeEx, CoUninitialize,
        },
    },
    core::implement,
};

use super::{AudioCommand, AudioState};

#[implement(IAudioEndpointVolumeCallback)]
struct VolumeCallback {
    states: watch::Sender<AudioState>,
}

impl IAudioEndpointVolumeCallback_Impl for VolumeCallback_Impl {
    fn OnNotify(
        &self,
        notification: *mut AUDIO_VOLUME_NOTIFICATION_DATA,
    ) -> windows::core::Result<()> {
        if notification.is_null() {
            return Ok(());
        }
        // AUDIO_VOLUME_NOTIFICATION_DATA is packed, so fields must be read unaligned.
        let volume = unsafe { std::ptr::addr_of!((*notification).fMasterVolume).read_unaligned() };
        let muted = unsafe {
            std::ptr::addr_of!((*notification).bMuted)
                .read_unaligned()
                .as_bool()
        };
        self.states.send_replace(AudioState {
            volume: volume.clamp(0.0, 1.0),
            muted,
        });
        Ok(())
    }
}

struct EndpointBinding {
    device: IMMDevice,
    volume: IAudioEndpointVolume,
    callback: IAudioEndpointVolumeCallback,
}

impl EndpointBinding {
    fn new(device: IMMDevice, states: watch::Sender<AudioState>) -> Result<Self> {
        let volume: IAudioEndpointVolume = unsafe { device.Activate(CLSCTX_ALL, None) }
            .context("failed to activate IAudioEndpointVolume")?;
        let callback: IAudioEndpointVolumeCallback = VolumeCallback { states }.into();
        unsafe { volume.RegisterControlChangeNotify(&callback) }
            .context("RegisterControlChangeNotify failed")?;
        Ok(Self {
            device,
            volume,
            callback,
        })
    }
}

impl Drop for EndpointBinding {
    fn drop(&mut self) {
        if let Err(error) = unsafe { self.volume.UnregisterControlChangeNotify(&self.callback) } {
            tracing::warn!(%error, "UnregisterControlChangeNotify failed");
        }
    }
}

pub fn spawn(
    commands: mpsc::Receiver<AudioCommand>,
    states: watch::Sender<AudioState>,
) -> Result<thread::JoinHandle<()>> {
    let worker = thread::Builder::new()
        .name("remote-core-audio".into())
        .spawn(move || {
            if let Err(error) = run(commands, states) {
                tracing::error!(%error, "Core Audio worker stopped");
            }
        })
        .context("failed to spawn Core Audio worker")?;
    Ok(worker)
}

fn run(commands: mpsc::Receiver<AudioCommand>, states: watch::Sender<AudioState>) -> Result<()> {
    unsafe { CoInitializeEx(None, COINIT_MULTITHREADED) }
        .ok()
        .context("CoInitializeEx failed")?;
    let result = run_initialized(commands, states);
    unsafe { CoUninitialize() };
    result
}

fn run_initialized(
    commands: mpsc::Receiver<AudioCommand>,
    states: watch::Sender<AudioState>,
) -> Result<()> {
    let enumerator: IMMDeviceEnumerator =
        unsafe { CoCreateInstance(&MMDeviceEnumerator, None, CLSCTX_ALL) }
            .context("failed to create MMDeviceEnumerator")?;
    let mut endpoint = open_default_endpoint(&enumerator, states.clone()).ok();
    let mut last_device_id = endpoint
        .as_ref()
        .and_then(|binding| device_id(&binding.device).ok());
    let mut last_state = None;

    loop {
        match commands.recv_timeout(Duration::from_millis(100)) {
            Ok(AudioCommand::SetVolume(value)) => {
                ensure_endpoint(&enumerator, &states, &mut endpoint)?;
                if let Some(binding) = &endpoint {
                    unsafe {
                        binding
                            .volume
                            .SetMasterVolumeLevelScalar(value, ptr::null())
                    }
                    .context("SetMasterVolumeLevelScalar failed")?;
                }
            }
            Ok(AudioCommand::SetMute(muted)) => {
                ensure_endpoint(&enumerator, &states, &mut endpoint)?;
                if let Some(binding) = &endpoint {
                    unsafe { binding.volume.SetMute(muted, ptr::null()) }
                        .context("SetMute failed")?;
                }
            }
            Ok(AudioCommand::Shutdown) => break,
            Err(RecvTimeoutError::Disconnected) => break,
            Err(RecvTimeoutError::Timeout) => {}
        }

        // Endpoint callbacks report volume/mute immediately. Polling the default device ID
        // here is only for endpoint replacement and as a defensive state reconciliation.
        match unsafe { enumerator.GetDefaultAudioEndpoint(eRender, eMultimedia) } {
            Ok(device) => {
                let id = device_id(&device).ok();
                if id != last_device_id || endpoint.is_none() {
                    endpoint = EndpointBinding::new(device, states.clone()).ok();
                    last_device_id = id;
                    last_state = None;
                    tracing::info!("default audio output changed; rebound endpoint volume");
                }
            }
            Err(error) => {
                if endpoint.take().is_some() {
                    tracing::warn!(%error, "default audio output is unavailable");
                }
                last_device_id = None;
            }
        }

        if let Some(binding) = &endpoint
            && let Ok(current) = read_state(&binding.volume)
            && last_state != Some(current)
        {
            last_state = Some(current);
            states.send_replace(current);
        }
    }
    Ok(())
}

fn ensure_endpoint(
    enumerator: &IMMDeviceEnumerator,
    states: &watch::Sender<AudioState>,
    endpoint: &mut Option<EndpointBinding>,
) -> Result<()> {
    if endpoint.is_none() {
        *endpoint = Some(open_default_endpoint(enumerator, states.clone())?);
    }
    Ok(())
}

fn open_default_endpoint(
    enumerator: &IMMDeviceEnumerator,
    states: watch::Sender<AudioState>,
) -> Result<EndpointBinding> {
    let device = unsafe { enumerator.GetDefaultAudioEndpoint(eRender, eMultimedia) }
        .context("default multimedia output is unavailable")?;
    let binding = EndpointBinding::new(device, states.clone())?;
    if let Ok(current) = read_state(&binding.volume) {
        states.send_replace(current);
    }
    Ok(binding)
}

fn device_id(device: &IMMDevice) -> Result<String> {
    let id = unsafe { device.GetId() }.context("IMMDevice::GetId failed")?;
    let value = unsafe { id.to_string() }.context("audio device ID is not valid UTF-16")?;
    unsafe { windows::Win32::System::Com::CoTaskMemFree(Some(id.0.cast())) };
    Ok(value)
}

fn read_state(volume: &IAudioEndpointVolume) -> Result<AudioState> {
    let value = unsafe { volume.GetMasterVolumeLevelScalar() }
        .context("GetMasterVolumeLevelScalar failed")?;
    let muted = unsafe { volume.GetMute() }
        .context("GetMute failed")?
        .as_bool();
    Ok(AudioState {
        volume: value.clamp(0.0, 1.0),
        muted,
    })
}
