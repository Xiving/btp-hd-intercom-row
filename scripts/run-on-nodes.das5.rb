#!/usr/bin/env ruby

$root_dir=ENV['INTERCOM_ROW_DIR']
$bin_dir="#{$root_dir}/bin"

require("#{$bin_dir}/run-on-nodes.rb")
require("#{$bin_dir}/run-submit-on-nodes-das5.rb")

command = "prun -np #{$nrNodes} " +
    "-t #{$time} " + "#{build_constraints} " +
    "#{$extra_params.join} " + 
    "CLASSPATH=#{$classpath}:$CLASSPATH $INTERCOM_ROW_DIR/bin/run-script " +
    "-XX:MaxDirectMemorySize=50G " +
    "-Dibis.server.address=fs0.das5.cs.vu.nl " +
    "-Dibis.constellation.closed=true " +
    "-Dibis.pool.size=#{$nrNodes} -Dibis.server.port=#{$port} " +
    "-Dibis.constellation.distributed=true " +
    "-Dibis.pool.name=das5.#{$$} #{$configargs} #{$classname} #{$rest.join(" ")}\n"

# print command

exec(command)
