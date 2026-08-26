package se500;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

final class Inspection {
    String systemId;
    String panelName;
    String serialCode;
    LocalDateTime testTime;
    char status;
    String sourceFile;
    final List<Measurement> measurements = new ArrayList<>();
}
