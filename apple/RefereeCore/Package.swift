// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "RefereeCore",
    platforms: [.iOS(.v17), .watchOS(.v10), .macOS(.v14)],
    products: [
        .library(name: "RefereeCore", targets: ["RefereeCore"]),
    ],
    targets: [
        .target(name: "RefereeCore"),
        .testTarget(name: "RefereeCoreTests", dependencies: ["RefereeCore"]),
    ]
)
