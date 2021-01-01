source "https://rubygems.org"

gem "fastlane"
gem 'rufo'
gem 'digest-crc'
gem 'solargraph', group: :development

plugins_path = File.join(File.dirname(__FILE__), 'fastlane', 'Pluginfile')
eval_gemfile(plugins_path) if File.exist?(plugins_path)
