require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))
version = package["version"]

Pod::Spec.new do |spec|
  spec.name         = "react-native-traccar-client-sdk"
  spec.version      = version
  spec.summary      = package["description"]
  spec.homepage     = package["repository"]
  spec.license      = package["license"]
  spec.authors      = "Traccar"
  spec.platforms    = { :ios => "15.0" }
  spec.source       = { :git => "https://github.com/traccar/traccar-client-sdk.git", :tag => "v#{version}" }

  spec.source_files = "ios/**/*.{h,m,mm,swift}"
  spec.vendored_frameworks = "ios/TraccarClientSDK.xcframework"
  spec.libraries = "sqlite3"
  spec.swift_version = "5.0"
  spec.resource_bundles = {
    "TraccarClientSdkPrivacy" => ["ios/PrivacyInfo.xcprivacy"]
  }

  # Fetch the prebuilt Kotlin/Native XCFramework from the matching GitHub
  # release, unless a locally built copy has already been placed in ios/.
  spec.prepare_command = <<-CMD
    if [ ! -d "ios/TraccarClientSDK.xcframework" ]; then
      curl -L -f -o ios/TraccarClientSDK.xcframework.zip \
        "https://github.com/traccar/traccar-client-sdk/releases/download/v#{version}/TraccarClientSDK.xcframework.zip"
      unzip -q -o ios/TraccarClientSDK.xcframework.zip -d ios
      rm -f ios/TraccarClientSDK.xcframework.zip
    fi
  CMD

  spec.dependency "React-Core"
end
