#!/usr/bin/env ruby

$root_dir=ENV['INTERCOM_ROW_DIR']
$bin_dir="#{$root_dir}/bin"

require("#{$bin_dir}/submit-on-nodes.rb")
require("#{$bin_dir}/run-submit-on-nodes-das5.rb")

filename="correlate.job"

open(filename, 'w') do |f|
  f.puts "#!/bin/bash"
  f.puts "#SBATCH --time #{$time}"
  f.puts "#SBATCH -N #{$nrNodes}"
  # the following code does not work if you need a different number of devices 
  # per node.  Instead, we will select the nodes we want explicitly
  # if $gpuSelection.length > 0
  # then
  #   f.puts "#SBATCH -C [#{$nodes.join("&")}]"
  #   f.puts "#{$gpuSelection}"
  # end
  f.puts "#SBATCH -w #{$node_names.join(",")}"
  f.puts "#SBATCH --get-user-env"
  f.puts ""
  f.puts "CLASSPATH=#{$classpath}:$CLASSPATH srun $INTERCOM_ROW_DIR/bin/run-script " +
    "-XX:MaxDirectMemorySize=50G " +
    "-Dibis.server.address=fs0.das5.cs.vu.nl " +
    "-Dibis.constellation.closed=true " +
    "-Dibis.pool.size=#{$nrNodes} -Dibis.server.port=#{$port} " +
    "-Dibis.pool.name=das5.#{$$} #{$configargs} #{$classname} #{$rest.join(" ")}\n"
end

command = "sbatch #{filename}"

exec(command)
