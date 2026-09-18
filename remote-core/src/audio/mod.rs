#[cfg(target_os = "windows")]
mod windows_audio;

use std::{
    sync::{Mutex, mpsc},
    thread::JoinHandle,
};

use anyhow::{Result, anyhow};
use tokio::sync::watch;

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct AudioState {
    pub volume: f32,
    pub muted: bool,
}

impl Default for AudioState {
    fn default() -> Self {
        Self {
            volume: 0.5,
            muted: false,
        }
    }
}

#[derive(Debug)]
pub enum AudioCommand {
    SetVolume(f32),
    SetMute(bool),
    Shutdown,
}

pub struct AudioHandle {
    commands: mpsc::Sender<AudioCommand>,
    state: watch::Receiver<AudioState>,
    worker: Mutex<Option<JoinHandle<()>>>,
}

impl AudioHandle {
    pub fn spawn() -> Result<Self> {
        let (commands, command_rx) = mpsc::channel();
        let (state_tx, state) = watch::channel(AudioState::default());
        let worker = platform_spawn(command_rx, state_tx)?;
        Ok(Self {
            commands,
            state,
            worker: Mutex::new(Some(worker)),
        })
    }

    pub fn state(&self) -> AudioState {
        *self.state.borrow()
    }

    pub fn subscribe(&self) -> watch::Receiver<AudioState> {
        self.state.clone()
    }

    pub fn set_volume(&self, value: f32) -> Result<()> {
        if !(0.0..=1.0).contains(&value) {
            anyhow::bail!("volume must be between 0 and 1")
        }
        self.commands
            .send(AudioCommand::SetVolume(value))
            .map_err(|_| anyhow!("Core Audio worker is not available"))
    }

    pub fn set_mute(&self, muted: bool) -> Result<()> {
        self.commands
            .send(AudioCommand::SetMute(muted))
            .map_err(|_| anyhow!("Core Audio worker is not available"))
    }

    pub fn shutdown(&self) {
        let _ = self.commands.send(AudioCommand::Shutdown);
        let Some(worker) = self.worker.lock().unwrap().take() else {
            return;
        };
        if let Err(error) = worker.join() {
            tracing::error!(?error, "Core Audio worker panicked during shutdown");
        }
    }
}

#[cfg(target_os = "windows")]
fn platform_spawn(
    commands: mpsc::Receiver<AudioCommand>,
    states: watch::Sender<AudioState>,
) -> Result<JoinHandle<()>> {
    windows_audio::spawn(commands, states)
}

#[cfg(not(target_os = "windows"))]
fn platform_spawn(
    _commands: mpsc::Receiver<AudioCommand>,
    _states: watch::Sender<AudioState>,
) -> Result<JoinHandle<()>> {
    anyhow::bail!("Core Audio is available only on Windows")
}
