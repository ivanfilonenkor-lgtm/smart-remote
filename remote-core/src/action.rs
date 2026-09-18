use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum MouseButton {
    Left,
    Right,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum ButtonState {
    Down,
    Up,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum KeyboardKey {
    Backspace,
    Enter,
    Tab,
    Escape,
    Delete,
}

#[derive(Debug, Clone, PartialEq)]
pub enum Action {
    PointerMove {
        dx: f32,
        dy: f32,
    },
    MouseButton {
        button: MouseButton,
        state: ButtonState,
    },
    Scroll {
        x: f32,
        y: f32,
    },
    TypeText(String),
    KeyPress(KeyboardKey),
    SetVolume(f32),
    SetMute(bool),
    NavigateBack,
    PlayPause,
    ShowDesktop,
}
