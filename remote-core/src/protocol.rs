use std::collections::BTreeMap;

use serde::{Deserialize, Serialize};
use serde_json::Value;
use thiserror::Error;
use uuid::Uuid;

use crate::{
    PROTOCOL_VERSION,
    action::{Action, ButtonState, KeyboardKey, MouseButton},
};

pub const REALTIME_FRAME_LEN: usize = 30;

#[derive(Debug, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum ClientMessage {
    ClientHello(ClientHello),
    Control(ControlMessage),
    Heartbeat { sequence: u64 },
}

#[derive(Debug, Clone, Deserialize)]
pub struct ClientHello {
    pub protocol: u8,
    pub client_id: Uuid,
    pub device_name: String,
    #[serde(default)]
    pub capabilities: Vec<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ControlMessage {
    pub request_id: String,
    pub action: String,
    #[serde(default)]
    pub payload: BTreeMap<String, Value>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum ServerMessage {
    ServerHello {
        protocol: u8,
        server_id: Uuid,
        server_name: String,
        capabilities: &'static [&'static str],
        volume: f32,
        muted: bool,
    },
    Ack {
        request_id: String,
        ok: bool,
        #[serde(skip_serializing_if = "Option::is_none")]
        error: Option<ProtocolErrorBody>,
    },
    State {
        revision: u64,
        volume: f32,
        muted: bool,
    },
    AnimeState {
        #[serde(flatten)]
        state: AnimeVostState,
    },
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(default)]
pub struct AnimeVostState {
    pub revision: u64,
    pub available: bool,
    pub site_id: Option<String>,
    pub site_name: Option<String>,
    pub title: Option<String>,
    pub episode: Option<u32>,
    pub episode_count: Option<u32>,
    pub playing: bool,
    pub skip_available: bool,
    pub previous_available: bool,
    pub next_available: bool,
    pub auto_mode: bool,
    pub countdown_seconds: Option<u8>,
    pub message: Option<String>,
}

impl Default for AnimeVostState {
    fn default() -> Self {
        Self {
            revision: 0,
            available: false,
            site_id: None,
            site_name: None,
            title: None,
            episode: None,
            episode_count: None,
            playing: false,
            skip_available: false,
            previous_available: false,
            next_available: false,
            auto_mode: false,
            countdown_seconds: None,
            message: None,
        }
    }
}

impl AnimeVostState {
    pub fn sanitize(mut self) -> Self {
        self.site_id = self.site_id.and_then(|value| {
            let value = value.trim().chars().take(64).collect::<String>();
            (!value.is_empty()).then_some(value)
        });
        self.site_name = self.site_name.and_then(|value| {
            let value = value.trim().chars().take(80).collect::<String>();
            (!value.is_empty()).then_some(value)
        });
        self.title = self.title.and_then(|value| {
            let value = value.trim().chars().take(160).collect::<String>();
            (!value.is_empty()).then_some(value)
        });
        self.message = self.message.and_then(|value| {
            let value = value.trim().chars().take(160).collect::<String>();
            (!value.is_empty()).then_some(value)
        });
        self.countdown_seconds = self.countdown_seconds.map(|value| value.min(30));
        if let Some(count) = self.episode_count {
            self.episode_count = Some(count.min(10_000));
            self.episode = self.episode.map(|episode| episode.min(count.max(1)));
        } else {
            self.episode = self.episode.map(|episode| episode.min(10_000));
        }
        self
    }

    pub fn unavailable(revision: u64, auto_mode: bool) -> Self {
        Self {
            revision,
            auto_mode,
            ..Self::default()
        }
    }
}

#[derive(Debug, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum BrowserMessage {
    BrowserHello {
        protocol: u8,
        #[serde(default)]
        extension_version: String,
    },
    AnimeState {
        #[serde(flatten)]
        state: AnimeVostState,
    },
    Heartbeat {
        sequence: u64,
    },
}

#[derive(Debug, Clone, Serialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum BrowserServerMessage {
    BrowserHello {
        protocol: u8,
    },
    AnimeCommand {
        request_id: String,
        action: &'static str,
        #[serde(skip_serializing_if = "Option::is_none")]
        enabled: Option<bool>,
    },
}

pub fn browser_command_from_control(
    message: &ControlMessage,
) -> Option<Result<BrowserServerMessage, ProtocolError>> {
    let action = match message.action.as_str() {
        "anime_previous" => "previous",
        "anime_play_pause" => "play_pause",
        "anime_next" => "next",
        "anime_skip" => "skip",
        "anime_cancel_next" => "cancel_next",
        "anime_set_auto_mode" => {
            let enabled = message
                .payload
                .get("enabled")
                .and_then(Value::as_bool)
                .ok_or_else(|| ProtocolError::InvalidPayload("missing boolean 'enabled'".into()));
            return Some(enabled.map(|enabled| BrowserServerMessage::AnimeCommand {
                request_id: message.request_id.clone(),
                action: "set_auto_mode",
                enabled: Some(enabled),
            }));
        }
        _ => return None,
    };
    Some(Ok(BrowserServerMessage::AnimeCommand {
        request_id: message.request_id.clone(),
        action,
        enabled: None,
    }))
}

#[derive(Debug, Clone, Serialize)]
pub struct ProtocolErrorBody {
    pub code: &'static str,
    pub message: String,
}

impl ServerMessage {
    pub fn ok(request_id: impl Into<String>) -> Self {
        Self::Ack {
            request_id: request_id.into(),
            ok: true,
            error: None,
        }
    }

    pub fn error(
        request_id: impl Into<String>,
        code: &'static str,
        message: impl Into<String>,
    ) -> Self {
        Self::Ack {
            request_id: request_id.into(),
            ok: false,
            error: Some(ProtocolErrorBody {
                code,
                message: message.into(),
            }),
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RealtimeKind {
    Pointer,
    Scroll,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct RealtimeFrame {
    pub kind: RealtimeKind,
    pub stream_id: u32,
    pub sequence: u32,
    pub client_timestamp_us: u64,
    pub cumulative_x: f32,
    pub cumulative_y: f32,
}

#[derive(Debug, Error, PartialEq)]
pub enum ProtocolError {
    #[error("expected {REALTIME_FRAME_LEN} bytes, got {0}")]
    InvalidRealtimeLength(usize),
    #[error("unsupported protocol version {0}")]
    UnsupportedVersion(u8),
    #[error("unknown realtime kind {0}")]
    UnknownRealtimeKind(u8),
    #[error("realtime displacement must be finite")]
    NonFiniteRealtimeValue,
    #[error("invalid action payload: {0}")]
    InvalidPayload(String),
    #[error("unknown action {0}")]
    UnknownAction(String),
}

impl RealtimeFrame {
    pub fn decode(bytes: &[u8]) -> Result<Self, ProtocolError> {
        if bytes.len() != REALTIME_FRAME_LEN {
            return Err(ProtocolError::InvalidRealtimeLength(bytes.len()));
        }
        if bytes[0] != PROTOCOL_VERSION {
            return Err(ProtocolError::UnsupportedVersion(bytes[0]));
        }
        let kind = match bytes[1] {
            1 => RealtimeKind::Pointer,
            2 => RealtimeKind::Scroll,
            other => return Err(ProtocolError::UnknownRealtimeKind(other)),
        };
        let stream_id = u32::from_le_bytes(bytes[2..6].try_into().unwrap());
        let sequence = u32::from_le_bytes(bytes[6..10].try_into().unwrap());
        let client_timestamp_us = u64::from_le_bytes(bytes[10..18].try_into().unwrap());
        let cumulative_x = f32::from_le_bytes(bytes[18..22].try_into().unwrap());
        let cumulative_y = f32::from_le_bytes(bytes[22..26].try_into().unwrap());
        if !cumulative_x.is_finite() || !cumulative_y.is_finite() {
            return Err(ProtocolError::NonFiniteRealtimeValue);
        }
        Ok(Self {
            kind,
            stream_id,
            sequence,
            client_timestamp_us,
            cumulative_x,
            cumulative_y,
        })
    }

    #[cfg(test)]
    pub fn encode(self) -> [u8; REALTIME_FRAME_LEN] {
        let mut bytes = [0_u8; REALTIME_FRAME_LEN];
        bytes[0] = PROTOCOL_VERSION;
        bytes[1] = match self.kind {
            RealtimeKind::Pointer => 1,
            RealtimeKind::Scroll => 2,
        };
        bytes[2..6].copy_from_slice(&self.stream_id.to_le_bytes());
        bytes[6..10].copy_from_slice(&self.sequence.to_le_bytes());
        bytes[10..18].copy_from_slice(&self.client_timestamp_us.to_le_bytes());
        bytes[18..22].copy_from_slice(&self.cumulative_x.to_le_bytes());
        bytes[22..26].copy_from_slice(&self.cumulative_y.to_le_bytes());
        bytes
    }
}

impl TryFrom<ControlMessage> for Action {
    type Error = ProtocolError;

    fn try_from(message: ControlMessage) -> Result<Self, Self::Error> {
        let string = |name: &str| {
            message
                .payload
                .get(name)
                .and_then(Value::as_str)
                .ok_or_else(|| ProtocolError::InvalidPayload(format!("missing string '{name}'")))
        };
        let boolean = |name: &str| {
            message
                .payload
                .get(name)
                .and_then(Value::as_bool)
                .ok_or_else(|| ProtocolError::InvalidPayload(format!("missing boolean '{name}'")))
        };

        match message.action.as_str() {
            "mouse_button" => {
                let button = match string("button")? {
                    "left" => MouseButton::Left,
                    "right" => MouseButton::Right,
                    other => {
                        return Err(ProtocolError::InvalidPayload(format!(
                            "unknown mouse button '{other}'"
                        )));
                    }
                };
                let state = match string("state")? {
                    "down" => ButtonState::Down,
                    "up" => ButtonState::Up,
                    other => {
                        return Err(ProtocolError::InvalidPayload(format!(
                            "unknown button state '{other}'"
                        )));
                    }
                };
                Ok(Action::MouseButton { button, state })
            }
            "set_volume" => {
                let value = message
                    .payload
                    .get("value")
                    .and_then(Value::as_f64)
                    .ok_or_else(|| {
                        ProtocolError::InvalidPayload("missing number 'value'".into())
                    })?;
                if !(0.0..=1.0).contains(&value) {
                    return Err(ProtocolError::InvalidPayload(
                        "volume must be between 0 and 1".into(),
                    ));
                }
                Ok(Action::SetVolume(value as f32))
            }
            "set_mute" => Ok(Action::SetMute(boolean("muted")?)),
            "type_text" => {
                let text = string("text")?;
                if text.is_empty() {
                    return Err(ProtocolError::InvalidPayload(
                        "text must not be empty".into(),
                    ));
                }
                if text.chars().count() > 4096 {
                    return Err(ProtocolError::InvalidPayload(
                        "text must contain at most 4096 characters".into(),
                    ));
                }
                Ok(Action::TypeText(text.to_owned()))
            }
            "key_press" => {
                let key = match string("key")? {
                    "backspace" => KeyboardKey::Backspace,
                    "enter" => KeyboardKey::Enter,
                    "tab" => KeyboardKey::Tab,
                    "escape" => KeyboardKey::Escape,
                    "delete" => KeyboardKey::Delete,
                    other => {
                        return Err(ProtocolError::InvalidPayload(format!(
                            "unknown keyboard key '{other}'"
                        )));
                    }
                };
                Ok(Action::KeyPress(key))
            }
            "back" => Ok(Action::NavigateBack),
            "play_pause" => Ok(Action::PlayPause),
            "show_desktop" => Ok(Action::ShowDesktop),
            other => Err(ProtocolError::UnknownAction(other.to_owned())),
        }
    }
}

#[cfg(test)]
mod tests {
    use std::path::PathBuf;

    use super::*;

    fn fixture(name: &str) -> String {
        let path = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
            .join("../protocol/fixtures")
            .join(name);
        std::fs::read_to_string(path).unwrap()
    }

    #[test]
    fn parses_client_hello_fixture_and_ignores_unknown_fields() {
        let mut value: Value = serde_json::from_str(&fixture("client-hello.json")).unwrap();
        value["future_field"] = Value::Bool(true);
        let message: ClientMessage = serde_json::from_value(value).unwrap();
        let ClientMessage::ClientHello(hello) = message else {
            panic!("wrong message")
        };
        assert_eq!(hello.protocol, 1);
        assert_eq!(hello.device_name, "Living room phone");
    }

    #[test]
    fn parses_control_fixture() {
        let ClientMessage::Control(control) =
            serde_json::from_str(&fixture("control-mouse-down.json")).unwrap()
        else {
            panic!("wrong message")
        };
        assert_eq!(
            Action::try_from(control).unwrap(),
            Action::MouseButton {
                button: MouseButton::Left,
                state: ButtonState::Down
            }
        );
    }

    #[test]
    fn parses_unicode_keyboard_fixture() {
        let ClientMessage::Control(control) =
            serde_json::from_str(&fixture("control-type-text.json")).unwrap()
        else {
            panic!("wrong message")
        };
        assert_eq!(
            Action::try_from(control).unwrap(),
            Action::TypeText("Привет, Windows! 👋".to_owned())
        );
    }

    #[test]
    fn rejects_empty_or_oversized_keyboard_text() {
        let message = |text: String| ControlMessage {
            request_id: "keyboard-test".into(),
            action: "type_text".into(),
            payload: BTreeMap::from([("text".into(), Value::String(text))]),
        };
        assert!(Action::try_from(message(String::new())).is_err());
        assert!(Action::try_from(message("x".repeat(4097))).is_err());
    }

    #[test]
    fn converts_anime_controls_without_turning_them_into_os_actions() {
        let auto = ControlMessage {
            request_id: "anime-auto".into(),
            action: "anime_set_auto_mode".into(),
            payload: BTreeMap::from([("enabled".into(), Value::Bool(true))]),
        };
        let command = browser_command_from_control(&auto).unwrap().unwrap();
        assert_eq!(
            serde_json::to_value(command).unwrap(),
            serde_json::json!({
                "type": "anime_command",
                "request_id": "anime-auto",
                "action": "set_auto_mode",
                "enabled": true
            })
        );
        assert!(Action::try_from(auto).is_err());
    }

    #[test]
    fn sanitizes_browser_state_before_broadcasting() {
        let state = AnimeVostState {
            site_id: Some(format!("  {}  ", "s".repeat(100))),
            site_name: Some(format!("  {}  ", "n".repeat(100))),
            title: Some(format!("  {}  ", "x".repeat(200))),
            episode: Some(99),
            episode_count: Some(12),
            countdown_seconds: Some(200),
            ..AnimeVostState::default()
        }
        .sanitize();
        assert_eq!(state.site_id.unwrap().chars().count(), 64);
        assert_eq!(state.site_name.unwrap().chars().count(), 80);
        assert_eq!(state.title.unwrap().chars().count(), 160);
        assert_eq!(state.episode, Some(12));
        assert_eq!(state.countdown_seconds, Some(30));
    }

    #[test]
    fn anime_state_fixture_matches_browser_and_phone_wire_shape() {
        let BrowserMessage::AnimeState { state } =
            serde_json::from_str(&fixture("anime-state.json")).unwrap()
        else {
            panic!("wrong browser message")
        };
        assert_eq!(state.episode, Some(4));
        assert!(state.auto_mode);

        let serialized = serde_json::to_value(ServerMessage::AnimeState { state }).unwrap();
        assert_eq!(serialized["type"], "anime_state");
        assert_eq!(serialized["episode_count"], 12);
        assert!(serialized.get("state").is_none());
    }

    #[test]
    fn binary_fixture_matches_layout() {
        let expected_hex = fixture("realtime-pointer-v1.hex").trim().to_owned();
        let frame = RealtimeFrame {
            kind: RealtimeKind::Pointer,
            stream_id: 42,
            sequence: 7,
            client_timestamp_us: 1_000_000,
            cumulative_x: 1.5,
            cumulative_y: -2.25,
        };
        let actual = frame
            .encode()
            .iter()
            .map(|byte| format!("{byte:02x}"))
            .collect::<String>();
        assert_eq!(actual, expected_hex);
        assert_eq!(RealtimeFrame::decode(&frame.encode()).unwrap(), frame);
    }

    #[test]
    fn rejects_non_finite_and_wrong_version() {
        let mut frame = RealtimeFrame {
            kind: RealtimeKind::Pointer,
            stream_id: 1,
            sequence: 1,
            client_timestamp_us: 1,
            cumulative_x: f32::NAN,
            cumulative_y: 0.0,
        }
        .encode();
        assert_eq!(
            RealtimeFrame::decode(&frame).unwrap_err(),
            ProtocolError::NonFiniteRealtimeValue
        );
        frame[0] = 9;
        assert_eq!(
            RealtimeFrame::decode(&frame).unwrap_err(),
            ProtocolError::UnsupportedVersion(9)
        );
    }
}
