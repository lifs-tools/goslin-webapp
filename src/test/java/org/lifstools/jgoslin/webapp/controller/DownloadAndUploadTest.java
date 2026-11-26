package org.lifstools.jgoslin.webapp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.lifstools.jgoslin.webapp.domain.NameEntry;
import org.lifstools.jgoslin.webapp.domain.StoredResult;
import org.lifstools.jgoslin.webapp.services.ResultStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The TSV download and the file upload without JavaScript. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(profiles = {"test", "dev"})
class DownloadAndUploadTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ResultStore store;

    @Test
    void missingTsvIsNotFoundAndNeverAnotherFile() throws Exception {
        // the entries are stored, the TSV is not (the first parse failed): the download must not serve the JSON
        StoredResult stored = store.create(List.of(NameEntry.of(1, "PC 32:0")));
        mockMvc.perform(get("/download/" + stored.id() + "/OUTPUT_TSV_FILE"))
                .andExpect(status().isNotFound());
    }

    private static byte[] withBom(byte[] bom, String text, Charset charset) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(bom);
        out.write(text.getBytes(charset));
        return out.toByteArray();
    }

    private Document uploadAndView(byte[] content) throws Exception {
        String location = mockMvc.perform(multipart("/validatefile")
                .file(new MockMultipartFile("file", "names.txt", "text/plain", content)))
                .andExpect(status().isSeeOther())
                .andReturn().getResponse().getHeader("Location");
        return Jsoup.parse(mockMvc.perform(get(location)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void utf16LittleEndianFileIsRead() throws Exception {
        Document doc = uploadAndView(withBom(new byte[]{(byte) 0xFF, (byte) 0xFE}, "PC 32:0\r\nTG 52:2\r\n",
                StandardCharsets.UTF_16LE));
        assertThat(doc.select("section#fix")).isEmpty();
        assertThat(doc.select("tr.result-row")).hasSize(2);
    }

    @Test
    void utf16BigEndianFileIsRead() throws Exception {
        Document doc = uploadAndView(withBom(new byte[]{(byte) 0xFE, (byte) 0xFF}, "PC 32:0\nTG 52:2\n",
                StandardCharsets.UTF_16BE));
        assertThat(doc.select("section#fix")).isEmpty();
        assertThat(doc.select("tr.result-row")).hasSize(2);
    }

    @Test
    void utf8FileWithAndWithoutBomIsRead() throws Exception {
        Document doc = uploadAndView(withBom(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, "PC 32:0\nTG 52:2\n",
                StandardCharsets.UTF_8));
        assertThat(doc.select("section#fix")).isEmpty();
        assertThat(doc.select("tr.result-row")).hasSize(2);
        doc = uploadAndView("PC 32:0\nTG 52:2\n".getBytes(StandardCharsets.UTF_8));
        assertThat(doc.select("tr.result-row")).hasSize(2);
    }

    @Test
    void helpSaysOneNamePerLineWithoutOtherColumns() throws Exception {
        Document en = Jsoup.parse(mockMvc.perform(get("/")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        assertThat(en.getElementById("lipids-help").text()).contains("one name per line, no other columns");
        Document de = Jsoup.parse(mockMvc.perform(get("/?lang=de")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        assertThat(de.getElementById("lipids-help").text()).contains("einen Namen pro Zeile, ohne weitere Spalten");
    }
}
