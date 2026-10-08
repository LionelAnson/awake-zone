//! Physical-pixel placement recovery. Normal dragging must not trigger centering.
#[derive(Clone, Copy)]
pub struct Area {
    pub x: i32,
    pub y: i32,
    pub width: u32,
    pub height: u32,
    pub scale: f64,
}

pub fn recover(position: (i32, i32), size: (u32, u32), areas: &[Area]) -> (i32, i32) {
    let (x, y) = (i64::from(position.0), i64::from(position.1));
    let (w, h) = (i64::from(size.0), i64::from(size.1));
    let valid = || areas.iter().filter(|a| a.width > 0 && a.height > 0);
    // Keep a usable portion of the header on any monitor. This tolerates native
    // invisible borders, taskbar overlap and intentional cross-monitor placement.
    if valid().any(|a| {
        let left = i64::from(a.x);
        let top = i64::from(a.y);
        let grip_w = ((64.0 * a.scale).ceil() as i64).max(1).min(w);
        let grip_h = ((24.0 * a.scale).ceil() as i64).max(1).min(h);
        let header_h = ((48.0 * a.scale).ceil() as i64).max(grip_h).min(h);
        (x + w).min(left + i64::from(a.width)) - x.max(left) >= grip_w
            && (y + header_h).min(top + i64::from(a.height)) - y.max(top) >= grip_h
    }) {
        return position;
    }
    // A disconnected display or inaccessible header: recover with the smallest
    // displacement, using the nearest work area rather than the primary center.
    valid()
        .map(|a| {
            let left = i64::from(a.x);
            let top = i64::from(a.y);
            let nx = x.clamp(left, left + (i64::from(a.width) - w).max(0));
            let ny = y.clamp(top, top + (i64::from(a.height) - h).max(0));
            let distance = i128::from(nx - x).pow(2) + i128::from(ny - y).pow(2);
            (distance, (nx as i32, ny as i32))
        })
        .min_by_key(|candidate| candidate.0)
        .map(|candidate| candidate.1)
        .unwrap_or(position)
}

#[cfg(test)]
mod tests {
    use super::*;
    fn screen(x: i32, y: i32, width: u32, height: u32) -> Area {
        Area {
            x,
            y,
            width,
            height,
            scale: 1.0,
        }
    }
    #[test]
    fn preserves_normal_edge_and_taskbar_placement() {
        let areas = [screen(0, 0, 1920, 1040)];
        for p in [(300, 200), (-8, -8), (1700, 900), (1580, 920)] {
            assert_eq!(recover(p, (348, 180), &areas), p);
        }
        // Rest-note growth near the bottom must not move the card.
        assert_eq!(recover((1500, 870), (348, 230), &areas), (1500, 870));
    }
    #[test]
    fn preserves_cross_monitor_and_negative_coordinates() {
        let areas = [screen(-1920, 0, 1920, 1040), screen(0, 0, 1920, 1040)];
        assert_eq!(recover((-100, 200), (348, 180), &areas), (-100, 200));
        assert_eq!(recover((-1900, 200), (348, 180), &areas), (-1900, 200));
    }
    #[test]
    fn disconnected_screen_recovers_to_nearest_edge() {
        let areas = [screen(0, 0, 1920, 1040)];
        assert_eq!(recover((2400, 700), (348, 180), &areas), (1572, 700));
        assert_eq!(recover((-900, 700), (348, 180), &areas), (0, 700));
        assert_eq!(recover((800, -160), (348, 180), &areas), (800, 0));
        assert_eq!(recover((800, 1030), (348, 180), &areas), (800, 860));
    }
    #[test]
    fn gap_uses_nearest_monitor_not_primary() {
        let areas = [screen(0, 0, 1000, 1000), screen(2000, 0, 1000, 1000)];
        assert_eq!(recover((1700, 400), (200, 180), &areas), (2000, 400));
    }
    #[test]
    fn scales_grip_and_handles_oversize_and_empty_areas() {
        let area = Area {
            scale: 2.0,
            ..screen(0, 0, 1920, 1040)
        };
        assert_eq!(recover((1840, 200), (696, 360), &[area]), (1224, 200));
        assert_eq!(recover((3000, 2000), (2200, 1400), &[area]), (0, 0));
        assert_eq!(recover((3000, 2000), (348, 180), &[]), (3000, 2000));
    }
}
