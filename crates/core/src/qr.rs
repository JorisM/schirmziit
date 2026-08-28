//! The pairing link as a matrix of modules, so every surface that shows a
//! pairing code can show a QR instead of asking someone to type it.
//!
//! One encoder, in Rust, for the same reason the wire format lives here: the
//! dashboard, the parent phone and a self-hoster's browser must all draw the
//! same code, and three encoders in three languages is three chances to hand a
//! family a square that scans as something else. The server mints the payload
//! and sends the matrix with it; nothing on a client encodes anything.

/// Four light modules on every side. The quiet zone is not decoration: a
/// scanner locates a code by finding its border, and a QR drawn flush against
/// a card edge often will not read at all. It ships inside the matrix rather
/// than as a reminder in three renderers, because a reminder is the thing one
/// of them forgets.
pub const QUIET_ZONE: usize = 4;

/// A square of modules, row by row, `1` dark and `0` light — the quiet zone
/// included, so `size` is what a renderer draws and nothing more is needed.
///
/// Characters rather than booleans: this crosses the API as JSON, where 33
/// rows of `"1011…"` stay readable in a response a self-hoster is debugging,
/// and an array of 1681 booleans does not.
#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
#[cfg_attr(feature = "schema", derive(utoipa::ToSchema))]
pub struct QrCode {
    /// Modules per side, quiet zone included.
    pub size: u32,
    /// `size` strings of `size` characters each.
    pub rows: Vec<String>,
}

/// Medium error correction: the level libqrencode and most phone cameras
/// assume, and enough redundancy for a code read off a laptop screen at an
/// angle. Higher levels buy nothing here — nobody prints this on a mug.
const ECC: qrcodegen::QrCodeEcc = qrcodegen::QrCodeEcc::Medium;

/// `None` when the payload does not fit in any QR version — 2953 bytes, which
/// a `schirmziit://enroll?url=…&code=…` link reaches only if a self-hoster's
/// public URL is pathological. The caller keeps the code and the URL on screen
/// as text either way, so a payload this crate cannot draw costs a scan, never
/// the pairing.
pub fn encode(payload: &str) -> Option<QrCode> {
    let code = qrcodegen::QrCode::encode_text(payload, ECC).ok()?;
    let side = code.size();
    let size = side as usize + 2 * QUIET_ZONE;

    let rows = (0..size)
        .map(|y| {
            (0..size)
                .map(|x| {
                    // `get_module` answers false outside the matrix, which is
                    // exactly the quiet zone.
                    let dark =
                        code.get_module(x as i32 - QUIET_ZONE as i32, y as i32 - QUIET_ZONE as i32);
                    if dark { '1' } else { '0' }
                })
                .collect()
        })
        .collect();

    Some(QrCode {
        size: size as u32,
        rows,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    /// A realistic minted link: the scheme both agents listen for, a
    /// self-hoster's URL, and a six-character code.
    const LINK: &str = "schirmziit://enroll?url=https://api.schirmziit.ch&code=K7MNPQ";

    /// `LINK` as this module draws it, `#` dark — kept in a file rather than
    /// in this source so a reviewer can see the square.
    ///
    /// What makes it a golden and not a recording of our own output: it was
    /// read back by an independent decoder (OpenCV's `QRCodeDetector`, which
    /// shares no code with the encoder) and yielded `LINK` character for
    /// character. That is the one property no assertion in this file can
    /// state — nothing here decodes — so it is stated here instead, and this
    /// test guards the matrix that was checked.
    const GOLDEN: &str = include_str!("../tests/golden/enroll_qr.txt");

    fn golden() -> Vec<String> {
        GOLDEN
            .lines()
            .map(|line| {
                line.chars()
                    .map(|c| if c == '#' { '1' } else { '0' })
                    .collect()
            })
            .collect()
    }

    #[test]
    fn a_pairing_link_draws_the_square_a_decoder_read_back() {
        let code = encode(LINK).expect("a 61-character link fits in a QR");

        assert_eq!(code.rows, golden());
    }

    #[test]
    fn the_quiet_zone_ships_inside_the_matrix() {
        let code = encode(LINK).expect("encodable");
        let size = code.size as usize;

        assert_eq!(size, 33 + 2 * QUIET_ZONE);
        for (y, row) in code.rows.iter().enumerate() {
            let border = y < QUIET_ZONE || y >= size - QUIET_ZONE;
            if border {
                assert!(row.chars().all(|c| c == '0'), "row {y} is not quiet");
            } else {
                assert!(row.starts_with("0000"), "row {y} has no left quiet zone");
                assert!(row.ends_with("0000"), "row {y} has no right quiet zone");
            }
        }
    }

    #[test]
    fn the_matrix_is_square_and_nothing_but_modules() {
        let code = encode(LINK).expect("encodable");

        assert_eq!(code.rows.len(), code.size as usize);
        for row in &code.rows {
            assert_eq!(row.chars().count(), code.size as usize);
            assert!(row.chars().all(|c| c == '0' || c == '1'));
        }
    }

    /// A code that always draws the same square would pair every phone with
    /// whichever child was first.
    #[test]
    fn two_codes_do_not_draw_the_same_square() {
        let one = encode("schirmziit://enroll?url=https://schirmziit.example&code=K7MPQ2");
        let two = encode("schirmziit://enroll?url=https://schirmziit.example&code=B4XR9T");

        assert_ne!(one, two);
    }

    #[test]
    fn a_payload_no_version_can_hold_is_not_a_square() {
        assert!(encode(&"x".repeat(3000)).is_none());
    }
}
