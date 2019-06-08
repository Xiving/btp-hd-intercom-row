package btp.hd.intercom_row.model.event;

import ibis.constellation.ActivityIdentifier;
import java.io.Serializable;
import lombok.Data;

@Data
public class InitEvent implements Serializable {

  private final ActivityIdentifier upper;
  private final ActivityIdentifier lower;
  private final ActivityIdentifier monitor;

}
