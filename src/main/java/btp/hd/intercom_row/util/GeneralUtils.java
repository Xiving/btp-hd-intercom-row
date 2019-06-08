package btp.hd.intercom_row.util;

import btp.hd.intercom_row.Activity.MonitorActivity;
import ibis.constellation.Context;

public class GeneralUtils {
    public static String label(String label, String host, int executor) {
      return label + "-" + host; //+ "-" + executor;
    }

    public static Context stencilContext(String host, int executor) {
      //return new Context(label(StencilActivity.LABEL, host, executor));
      return new Context("operation");
    }

    public static Context monitorContext(String host) {
      //return new Context(MonitorActivity.LABEL + "-" + host);
      return new Context("monitor");
    }
}
