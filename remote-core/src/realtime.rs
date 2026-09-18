use crate::{
    action::Action,
    protocol::{RealtimeFrame, RealtimeKind},
};

#[derive(Debug, Default)]
pub struct RealtimeAccumulator {
    stream_id: Option<u32>,
    last_sequence: Option<u32>,
    last_x: f32,
    last_y: f32,
}

impl RealtimeAccumulator {
    pub fn apply(&mut self, frame: RealtimeFrame) -> Option<Action> {
        if self.stream_id != Some(frame.stream_id) {
            self.stream_id = Some(frame.stream_id);
            self.last_sequence = None;
            self.last_x = 0.0;
            self.last_y = 0.0;
        }
        if self
            .last_sequence
            .is_some_and(|last| frame.sequence <= last)
        {
            return None;
        }
        let dx = frame.cumulative_x - self.last_x;
        let dy = frame.cumulative_y - self.last_y;
        self.last_sequence = Some(frame.sequence);
        self.last_x = frame.cumulative_x;
        self.last_y = frame.cumulative_y;
        if dx == 0.0 && dy == 0.0 {
            return None;
        }
        Some(match frame.kind {
            RealtimeKind::Pointer => Action::PointerMove { dx, dy },
            RealtimeKind::Scroll => Action::Scroll { x: dx, y: -dy },
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn frame(stream: u32, sequence: u32, x: f32, y: f32) -> RealtimeFrame {
        RealtimeFrame {
            kind: RealtimeKind::Pointer,
            stream_id: stream,
            sequence,
            client_timestamp_us: 0,
            cumulative_x: x,
            cumulative_y: y,
        }
    }

    #[test]
    fn cumulative_sample_recovers_conflated_displacement() {
        let mut accumulator = RealtimeAccumulator::default();
        assert_eq!(
            accumulator.apply(frame(1, 1, 2.0, 3.0)),
            Some(Action::PointerMove { dx: 2.0, dy: 3.0 })
        );
        assert_eq!(
            accumulator.apply(frame(1, 4, 9.0, 10.0)),
            Some(Action::PointerMove { dx: 7.0, dy: 7.0 })
        );
    }

    #[test]
    fn rejects_stale_sequence_and_resets_new_stream() {
        let mut accumulator = RealtimeAccumulator::default();
        accumulator.apply(frame(1, 5, 4.0, 4.0));
        assert_eq!(accumulator.apply(frame(1, 4, 8.0, 8.0)), None);
        assert_eq!(
            accumulator.apply(frame(2, 1, 1.0, -1.0)),
            Some(Action::PointerMove { dx: 1.0, dy: -1.0 })
        );
    }
}
