// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "RefereeCore",
    platforms: [.iOS(.v17), .watchOS(.v10), .macOS(.v14)],
    products: [
        .library(name: "RefereeCore", targets: ["RefereeCore"]),
        .library(name: "RefereeSession", targets: ["RefereeSession"]),
    ],
    targets: [
        .target(name: "RefereeCore"),
        .target(name: "RefereeSession", dependencies: ["RefereeCore"]),
        .testTarget(name: "RefereeCoreTests", dependencies: ["RefereeCore"]),
        .testTarget(name: "RefereeSessionTests", dependencies: ["RefereeSession", "RefereeCore"]),
    ]
)
