package manfred.bytedepth;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BdDialogAssetsTest {

    @Test
    void dialogAssetsAreIsolatedAndProvideAccessibleConfirmation() throws Exception {
        String css = classpathText("/static/css/bd-dialog.css");
        String js = classpathText("/static/js/bd-dialog.js");

        assertThat(css)
                .contains(".bd-dialog")
                .contains(".bd-dialog__surface")
                .contains(".bd-dialog::backdrop")
                .contains("font-family: var(--bd-font-sans")
                .doesNotContain("body {")
                .doesNotContain("\n* {");
        assertThat(js)
                .contains("document.createElement('dialog')")
                .contains("dialog.showModal()")
                .contains("textContent")
                .contains("window.BytedepthDialog")
                .contains("form[data-bd-confirm]")
                .contains("requestSubmit")
                .contains("form.requestSubmit && submitter")
                .contains("var acceptedForms = new WeakSet()")
                .doesNotContain("bdConfirmAccepted");
    }

    @Test
    void confirmationPagesUseTheSharedDialogInsteadOfNativeConfirm() throws Exception {
        List<String> templates = List.of(
                "/templates/admin/posts/list.html",
                "/templates/admin/series/list.html",
                "/templates/admin/series/detail.html",
                "/templates/admin/tags/list.html",
                "/templates/admin/users/list.html",
                "/templates/admin/ops/dashboard.html"
        );

        for (String template : templates) {
            String html = classpathText(template);
            assertThat(html).as(template)
                    .doesNotContain("window.confirm(")
                    .doesNotContain("return confirm(");
        }

        String nav = classpathText("/templates/fragments/nav.html");
        assertThat(nav)
                .contains("@{/css/bd-dialog.css}")
                .contains("@{/js/bd-dialog.js}");
        assertThat(classpathText("/templates/admin/posts/list.html")).contains("data-bd-confirm");
        assertThat(classpathText("/templates/admin/ops/dashboard.html"))
                .contains("window.BytedepthDialog.confirm");
    }

    private String classpathText(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("classpath resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
