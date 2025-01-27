/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.validation.et;

import jakarta.xml.bind.ValidationEvent;
import no.rutebanken.anshar.routes.validation.validators.et.RecordedActualArrivalTimeValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class RecordedActualArrivalTimeValidatorTest extends CustomValidatorTest {

    private static RecordedActualArrivalTimeValidator validator;
    private final String fieldName = "ActualArrivalTime";
    private final String comparisonField = "ActualDepartureTime";

    @BeforeAll
    public static void init() {
        validator = new RecordedActualArrivalTimeValidator();
    }

    @Test
    public void testArrivalOnly() throws Exception{
        String xml = createXml(fieldName, "2018-04-16T10:00:00+02:00");

        assertNull(validator.isValid(createXmlNode(xml).getFirstChild()), "Valid "+fieldName+" flagged as invalid");
    }


    @Test
    public void testArrivalEqualDeparture() throws Exception{
        String arrival = createXml(fieldName, "2018-04-16T10:00:00+02:00");
        String departure = createXml(comparisonField, "2018-04-16T10:00:00+02:00");

        String xml = "<PLACEHOLDER>" + arrival + departure + "</PLACEHOLDER>";

        assertNull(validator.isValid(createXmlNode(xml).getFirstChild()), "Valid "+fieldName+" flagged as invalid");
    }

    @Test
    public void testArrivalBeforeDeparture() throws Exception{
        String arrival = createXml(fieldName, "2018-04-16T10:00:00+02:00");
        String departure = createXml(comparisonField, "2018-04-16T10:02:00+02:00");

        String xml = "<PLACEHOLDER>" + arrival + departure + "</PLACEHOLDER>";

        assertNull(validator.isValid(createXmlNode(xml).getFirstChild()), "Valid "+fieldName+" flagged as invalid");
    }

    @Test
    public void testArrivalAfterDeparture() throws Exception{
        String arrival = createXml(fieldName, "2018-04-16T10:02:00+02:00");
        String departure = createXml(comparisonField, "2018-04-16T10:00:00+02:00");

        String xml = "<dummy>" + arrival + departure + "</dummy>";

        final ValidationEvent valid = validator.isValid(createXmlNode(xml).getFirstChild());
        assertNotNull(valid, "Invalid "+fieldName+" flagged as valid");
    }
}
