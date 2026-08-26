package se500;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

/**
 * StAX, nem DOM. A mintafájl ~1.5 MB és 1184 feature, felesleges az egészet fára rakni.
 */
final class SpiParser {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss");

    Inspection parse(Path file) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

        Inspection insp = new Inspection();
        insp.sourceFile = file.getFileName().toString();

        String image = null;
        String location = null;
        Measurement current = null;

        try (FileInputStream in = new FileInputStream(file.toFile())) {
            XMLStreamReader r = factory.createXMLStreamReader(in);
            try {
                while (r.hasNext()) {
                    int ev = r.next();
                    if (ev == XMLStreamConstants.START_ELEMENT) {
                        switch (r.getLocalName()) {
                            case "SystemId" -> insp.systemId = r.getElementText().trim();
                            case "Panel" -> {
                                insp.panelName = attr(r, "Name");
                                insp.serialCode = attr(r, "Code");
                                insp.testTime = LocalDateTime.parse(attr(r, "TestTime"), TIME);
                                String st = attr(r, "Status");
                                insp.status = st.isEmpty() ? '?' : st.charAt(0);
                            }
                            case "Image" -> image = attr(r, "Name");
                            case "Location" -> location = attr(r, "Name");
                            case "Feature" -> {
                                current = new Measurement();
                                current.imageName = image;
                                current.locationName = location;
                                current.featureName = attr(r, "Name");
                            }
                            case "Height" -> {
                                if (current != null) fillMetric(r, current, 'H');
                            }
                            case "Area" -> {
                                if (current != null) fillMetric(r, current, 'A');
                            }
                            case "Volume" -> {
                                if (current != null) fillMetric(r, current, 'V');
                            }
                            default -> {
                            }
                        }
                    } else if (ev == XMLStreamConstants.END_ELEMENT
                            && "Feature".equals(r.getLocalName())
                            && current != null) {
                        if (current.hasHeight && current.hasArea && current.hasVolume) {
                            insp.measurements.add(current);
                        }
                        current = null;
                    }
                }
            } finally {
                r.close();
            }
        }
        return insp;
    }

    private static void fillMetric(XMLStreamReader r, Measurement m, char which) {
        Float value = num(attr(r, "Value"));
        if (value == null) {
            return;
        }
        switch (which) {
            case 'H' -> {
                m.height = value;
                m.hasHeight = true;
                m.hUpFail = numOrNan(r, "UpFail");
                m.hUpWarn = numOrNan(r, "UpWarn");
                m.hTarget = numOrNan(r, "Target");
                m.hLowWarn = numOrNan(r, "LowWarn");
                m.hLowFail = numOrNan(r, "LowFail");
            }
            case 'A' -> {
                m.area = value;
                m.hasArea = true;
                m.aUpFail = numOrNan(r, "UpFail");
                m.aUpWarn = numOrNan(r, "UpWarn");
                m.aTarget = numOrNan(r, "Target");
                m.aLowWarn = numOrNan(r, "LowWarn");
                m.aLowFail = numOrNan(r, "LowFail");
            }
            case 'V' -> {
                m.volume = value;
                m.hasVolume = true;
                m.vUpFail = numOrNan(r, "UpFail");
                m.vUpWarn = numOrNan(r, "UpWarn");
                m.vTarget = numOrNan(r, "Target");
                m.vLowWarn = numOrNan(r, "LowWarn");
                m.vLowFail = numOrNan(r, "LowFail");
            }
            default -> {
            }
        }
    }

    private static String attr(XMLStreamReader r, String name) {
        String v = r.getAttributeValue(null, name);
        return v == null ? "" : v.trim();
    }

    private static Float num(String s) {
        if (s.isEmpty() || "N/A".equalsIgnoreCase(s)) {
            return null;
        }
        return Float.valueOf(s);
    }

    private static float numOrNan(XMLStreamReader r, String name) {
        Float n = num(attr(r, name));
        return n == null ? Float.NaN : n;
    }
}
