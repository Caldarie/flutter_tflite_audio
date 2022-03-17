#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html.
# Run `pod lib lint tflite_audio.podspec' to validate before publishing.
#
Pod::Spec.new do |s|
  s.name             = 'tflite_audio'
  s.version          = '0.3.0'
  s.summary          = 'A new flutter plugin project.'
  s.description      = <<-DESC
A new flutter plugin project.
                       DESC
  s.homepage         = 'http://example.com'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'Your Company' => 'email@example.com' }
  s.source           = { :path => '.' }
  s.source_files = 'Classes/**/*'
  s.dependency 'Flutter'
  s.dependency 'TensorFlowLiteSwift', '~> 2.6.0'
  s.dependency 'RxSwift', '6.5.0'
  s.dependency 'RxCocoa', '6.5.0'
  s.dependency 'RosaKit'
  # s.dependency 'TensorFlowLiteSwift'
  # s.dependency 'TensorFlowLiteSelectTfOps'
  # s.dependency 'TensorFlowLiteSwift', '~> 2.4.0'
  # s.dependency 'TensorFlowLiteSelectTfOps', '~> 2.4.0'
  # s.dependency 'TensorFlowLiteSwift', '~> 0.0.1-nightly'
  # s.dependency 'TensorFlowLiteSelectTfOps', '~> 0.0.1-nightly'
  s.platform = :ios, '12.0'
  s.static_framework = true

  # Flutter.framework does not contain a i386 slice. Only x86_64 simulators are supported.
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES', 'VALID_ARCHS[sdk=iphonesimulator*]' => 'x86_64' }
  s.swift_version = '5.0'
end
