package btp.hd.intercom_row.util;

import btp.hd.intercom_row.Activity.MonitorActivity;
import btp.hd.intercom_row.Activity.StencilActivity;
import ibis.constellation.Context;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GeneralUtils {
    public static String label(String label, String host, int executor) {
      return label + "-" + host + "-" + executor;
    }

    public static Context stencilContext(String host, int executor) {
        String label = label(StencilActivity.LABEL, host, executor);
        log.info("Creating context with label: {}", label);
      return new Context(label);
    }

    public static Context monitorContext(String host) {
        String label = label(MonitorActivity.LABEL, host, 1)
        log.info("Creating context with label: {}", label);
        return new Context(label);
    }
}
