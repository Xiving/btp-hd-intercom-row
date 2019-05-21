package btp.hd.simple_row.model.event;

import ibis.constellation.ActivityIdentifier;
import lombok.Data;

import java.io.Serializable;

@Data
public class LinkEvent implements Serializable {

    private final ActivityIdentifier identifier;

}
