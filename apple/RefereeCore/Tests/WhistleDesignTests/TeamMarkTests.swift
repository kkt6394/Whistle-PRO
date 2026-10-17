import Testing
@testable import RefereeSession
@testable import WhistleDesign

struct TeamMarkTests {
    @Test func halvesAreSameWhenNoSecondary() {
        let h = TeamMarkColors.halves(primary: .red, secondary: nil, onDark: false)
        #expect(h.left == h.right)
        #expect(h.left == RGB(red: TeamColor.red.red, green: TeamColor.red.green, blue: TeamColor.red.blue))
    }

    @Test func halvesSplitPrimaryLeftSecondaryRight() {
        let h = TeamMarkColors.halves(primary: .red, secondary: .black, onDark: false)
        #expect(h.left == RGB(hex: 0xFF3B30))
        #expect(h.right == RGB(hex: 0x000000))
    }

    @Test func onDarkLiftsEachHalfSeparately() {
        let h = TeamMarkColors.halves(primary: .black, secondary: .white, onDark: true)
        #expect(h.left == TeamColor.black.markOnDark)
        #expect(h.left != RGB(hex: 0x000000))
        #expect(h.right == RGB(hex: 0xFFFFFF))
    }
}
