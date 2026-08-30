package org.cttelsamicsterrassa.data.api.rest.importjob;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.cttelsamicsterrassa.data.core.domain.shared.port.ImportSourcesPort;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ImportJobControllerTest {

    private static ImportJobController controllerWith(ImportSourcesPort port) {
        return new ImportJobController(port);
    }

    @Test
    void listSourcesReturnsAllEnumValues() {
        ImportJobController controller = controllerWith(() -> List.of(ImportSource.values()));

        ResponseEntity<?> response = controller.listSources();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertInstanceOf(List.class, response.getBody());
        List<?> body = (List<?>) response.getBody();
        assertEquals(3, body.size());
    }

    @Test
    void listSourcesMapsToDto() {
        ImportJobController controller = controllerWith(() -> List.of(ImportSource.RFETM, ImportSource.BCNESA));

        ResponseEntity<?> response = controller.listSources();

        List<?> body = (List<?>) response.getBody();
        assertNotNull(body);
        assertEquals(2, body.size());
        ImportSourceDto first = (ImportSourceDto) body.get(0);
        assertEquals("RFETM", first.id());
        ImportSourceDto second = (ImportSourceDto) body.get(1);
        assertEquals("BCNESA", second.id());
    }

    @Test
    void listSourcesReturnsEmptyWhenPortReturnsEmpty() {
        ImportJobController controller = controllerWith(List::of);

        ResponseEntity<?> response = controller.listSources();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(), response.getBody());
    }

    @Test
    void importSourceDtoFromRfetm() {
        ImportSourceDto dto = ImportSourceDto.from(ImportSource.RFETM);
        assertEquals("RFETM", dto.id());
        assertNotNull(dto.label());
    }

    @Test
    void importSourceDtoFromBcnesa() {
        ImportSourceDto dto = ImportSourceDto.from(ImportSource.BCNESA);
        assertEquals("BCNESA", dto.id());
    }

    @Test
    void importSourceDtoFromFctt() {
        ImportSourceDto dto = ImportSourceDto.from(ImportSource.FCTT);
        assertEquals("FCTT", dto.id());
    }
}
