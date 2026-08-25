import XCTest
@testable import SchirmziitKit

final class MotionTests: XCTestCase {
    func testReducedMotionYieldsNoAnimation() {
        // nil is SwiftUI's "apply instantly". Not a shorter duration: a fast
        // animation is still an animation, and the setting asks for none.
        XCTAssertNil(Motion.animation(Motion.hero, reduceMotion: true))
        XCTAssertNotNil(Motion.animation(Motion.hero, reduceMotion: false))
    }

    func testDurationsStayInsideTheBudget() {
        // The motion budget: 200–400 ms typical, 600 ms for the hero count-up.
        for duration in [Motion.fast, Motion.base, Motion.slow] {
            XCTAssertLessThanOrEqual(duration, 0.4)
        }
        XCTAssertLessThanOrEqual(Motion.hero, 0.6)
    }

    func testStaggerDelayGrowsPerRow() {
        XCTAssertEqual(Motion.staggerDelay(0), 0)
        XCTAssertEqual(Motion.staggerDelay(3), 3 * Motion.staggerStep, accuracy: 0.0001)
    }
}
