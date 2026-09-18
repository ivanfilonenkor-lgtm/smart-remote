use std::{mem::size_of, sync::Mutex};

use anyhow::{Context, Result, ensure};
use windows::Win32::UI::Input::KeyboardAndMouse::{
    INPUT, INPUT_0, INPUT_KEYBOARD, INPUT_MOUSE, KEYBD_EVENT_FLAGS, KEYBDINPUT, KEYEVENTF_KEYUP,
    KEYEVENTF_UNICODE, MOUSE_EVENT_FLAGS, MOUSEEVENTF_HWHEEL, MOUSEEVENTF_LEFTDOWN,
    MOUSEEVENTF_LEFTUP, MOUSEEVENTF_MOVE, MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP,
    MOUSEEVENTF_WHEEL, MOUSEINPUT, SendInput, VIRTUAL_KEY, VK_BACK, VK_D, VK_DELETE, VK_ESCAPE,
    VK_LEFT, VK_LMENU, VK_LWIN, VK_MEDIA_PLAY_PAUSE, VK_RETURN, VK_TAB,
};

use crate::action::{Action, ButtonState, KeyboardKey, MouseButton};

use super::InputExecutor;

#[derive(Default)]
pub struct WindowsInputExecutor {
    residuals: Mutex<Residuals>,
}

#[derive(Default)]
struct Residuals {
    pointer_x: f32,
    pointer_y: f32,
    scroll_x: f32,
    scroll_y: f32,
}

impl InputExecutor for WindowsInputExecutor {
    fn execute(&self, action: &Action) -> Result<()> {
        match action {
            Action::PointerMove { dx, dy } => {
                let mut residuals = self.residuals.lock().unwrap();
                residuals.pointer_x += *dx;
                residuals.pointer_y += *dy;
                let x = residuals.pointer_x.trunc() as i32;
                let y = residuals.pointer_y.trunc() as i32;
                residuals.pointer_x -= x as f32;
                residuals.pointer_y -= y as f32;
                drop(residuals);
                if x != 0 || y != 0 {
                    send(&[mouse_input(x, y, 0, MOUSEEVENTF_MOVE)])?;
                }
            }
            Action::MouseButton { button, state } => {
                let flags = match (button, state) {
                    (MouseButton::Left, ButtonState::Down) => MOUSEEVENTF_LEFTDOWN,
                    (MouseButton::Left, ButtonState::Up) => MOUSEEVENTF_LEFTUP,
                    (MouseButton::Right, ButtonState::Down) => MOUSEEVENTF_RIGHTDOWN,
                    (MouseButton::Right, ButtonState::Up) => MOUSEEVENTF_RIGHTUP,
                };
                send(&[mouse_input(0, 0, 0, flags)])?;
            }
            Action::Scroll { x, y } => {
                let mut residuals = self.residuals.lock().unwrap();
                // Forty logical pixels equal one conventional Windows wheel detent.
                residuals.scroll_x += *x * 3.0;
                residuals.scroll_y += *y * 3.0;
                let x_units = residuals.scroll_x.trunc() as i32;
                let y_units = residuals.scroll_y.trunc() as i32;
                residuals.scroll_x -= x_units as f32;
                residuals.scroll_y -= y_units as f32;
                drop(residuals);
                let mut inputs = Vec::with_capacity(2);
                if x_units != 0 {
                    inputs.push(mouse_input(0, 0, x_units as u32, MOUSEEVENTF_HWHEEL));
                }
                if y_units != 0 {
                    inputs.push(mouse_input(0, 0, y_units as u32, MOUSEEVENTF_WHEEL));
                }
                if !inputs.is_empty() {
                    send(&inputs)?;
                }
            }
            Action::NavigateBack => send_chord(&[VK_LMENU, VK_LEFT])?,
            Action::PlayPause => send_chord(&[VK_MEDIA_PLAY_PAUSE])?,
            Action::ShowDesktop => send_chord(&[VK_LWIN, VK_D])?,
            Action::TypeText(text) => send_unicode_text(text)?,
            Action::KeyPress(key) => send_chord(&[virtual_key(*key)])?,
            Action::SetVolume(_) | Action::SetMute(_) => {
                anyhow::bail!("audio action was routed to the input executor")
            }
        }
        Ok(())
    }
}

fn mouse_input(dx: i32, dy: i32, data: u32, flags: MOUSE_EVENT_FLAGS) -> INPUT {
    INPUT {
        r#type: INPUT_MOUSE,
        Anonymous: INPUT_0 {
            mi: MOUSEINPUT {
                dx,
                dy,
                mouseData: data,
                dwFlags: flags,
                time: 0,
                dwExtraInfo: 0,
            },
        },
    }
}

fn keyboard_input(key: VIRTUAL_KEY, flags: KEYBD_EVENT_FLAGS) -> INPUT {
    INPUT {
        r#type: INPUT_KEYBOARD,
        Anonymous: INPUT_0 {
            ki: KEYBDINPUT {
                wVk: key,
                wScan: 0,
                dwFlags: flags,
                time: 0,
                dwExtraInfo: 0,
            },
        },
    }
}

fn unicode_input(code_unit: u16, flags: KEYBD_EVENT_FLAGS) -> INPUT {
    INPUT {
        r#type: INPUT_KEYBOARD,
        Anonymous: INPUT_0 {
            ki: KEYBDINPUT {
                wVk: VIRTUAL_KEY(0),
                wScan: code_unit,
                dwFlags: KEYBD_EVENT_FLAGS(flags.0 | KEYEVENTF_UNICODE.0),
                time: 0,
                dwExtraInfo: 0,
            },
        },
    }
}

fn send_unicode_text(text: &str) -> Result<()> {
    send(&unicode_inputs(text))
}

fn unicode_inputs(text: &str) -> Vec<INPUT> {
    let mut inputs = Vec::with_capacity(text.encode_utf16().count() * 2);
    for code_unit in text.encode_utf16() {
        inputs.push(unicode_input(code_unit, KEYBD_EVENT_FLAGS(0)));
        inputs.push(unicode_input(code_unit, KEYEVENTF_KEYUP));
    }
    inputs
}

fn virtual_key(key: KeyboardKey) -> VIRTUAL_KEY {
    match key {
        KeyboardKey::Backspace => VK_BACK,
        KeyboardKey::Enter => VK_RETURN,
        KeyboardKey::Tab => VK_TAB,
        KeyboardKey::Escape => VK_ESCAPE,
        KeyboardKey::Delete => VK_DELETE,
    }
}

fn send_chord(keys: &[VIRTUAL_KEY]) -> Result<()> {
    let mut inputs = Vec::with_capacity(keys.len() * 2);
    inputs.extend(
        keys.iter()
            .copied()
            .map(|key| keyboard_input(key, KEYBD_EVENT_FLAGS(0))),
    );
    inputs.extend(
        keys.iter()
            .rev()
            .copied()
            .map(|key| keyboard_input(key, KEYEVENTF_KEYUP)),
    );
    send(&inputs)
}

fn send(inputs: &[INPUT]) -> Result<()> {
    if inputs.is_empty() {
        return Ok(());
    }
    let sent = unsafe { SendInput(inputs, size_of::<INPUT>() as i32) };
    ensure!(
        sent == inputs.len() as u32,
        "SendInput inserted {sent}/{} events",
        inputs.len()
    );
    windows::core::Result::<()>::Ok(()).context("SendInput failed")?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn unicode_input_covers_bmp_and_surrogate_pairs() {
        // A is one UTF-16 code unit; the emoji is a surrogate pair. Every unit
        // produces a key-down and key-up event.
        assert_eq!(unicode_inputs("A😀").len(), 6);
    }
}
