#!/usr/bin/env ruby

root_dir=ENV['INTERCOM_ROW_DIR']
bin_dir="#{root_dir}/bin"

if ARGV.length < 2
    puts "not enough arguments"
    exit 1
end

basedir = ARGV[0]
className = ARGV[1]
rest = ARGV[2..-1]
classpath = `#{bin_dir}/create-class-path #{basedir}`.chomp
port = ENV['CONSTELLATION_PORT']

command = "#{bin_dir}/run-script-local -cp #{classpath}:$CLASSPATH -Xmx16G " +
    "-Dibis.server.address=localhost:#{port} " +
    "-Dibis.constellation.distributed=false " +
    "#{className} #{rest.join(" ")}\n"

print command

exec(command)
