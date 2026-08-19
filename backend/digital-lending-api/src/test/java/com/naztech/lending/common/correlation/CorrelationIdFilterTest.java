package com.naztech.lending.common.correlation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearContext() {
        CorrelationId.clear();
    }

    @Test
    void generatesCorrelationIdWhenClientSendsNone() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String boundDuringRequest = invokeAndCaptureBoundId(request, response);

        assertThat(boundDuringRequest).isNotBlank();
        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo(boundDuringRequest);
    }

    @Test
    void propagatesCorrelationIdSuppliedByClient() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HEADER, "mobile-app-7f3a91c4");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String boundDuringRequest = invokeAndCaptureBoundId(request, response);

        assertThat(boundDuringRequest).isEqualTo("mobile-app-7f3a91c4");
        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo("mobile-app-7f3a91c4");
    }

    @Test
    void replacesCorrelationIdContainingLogInjectionCharacters() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HEADER, "abc\n2026-01-01 ERROR forged log line");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String boundDuringRequest = invokeAndCaptureBoundId(request, response);

        assertThat(boundDuringRequest).doesNotContain("forged").doesNotContain("\n");
    }

    @Test
    void replacesCorrelationIdThatIsTooShortToBeMeaningful() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HEADER, "x1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String boundDuringRequest = invokeAndCaptureBoundId(request, response);

        assertThat(boundDuringRequest).isNotEqualTo("x1");
    }

    @Test
    void clearsCorrelationIdAfterTheRequestCompletes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        invokeAndCaptureBoundId(request, response);

        assertThat(CorrelationId.current()).isNull();
    }

    @Test
    void clearsCorrelationIdEvenWhenTheChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain failing = (req, res) -> {
            throw new IllegalStateException("downstream failure");
        };

        try {
            filter.doFilter(request, response, failing);
        } catch (Exception expected) {
            // the filter must not swallow it
        }

        assertThat(CorrelationId.current()).isNull();
    }

    private String invokeAndCaptureBoundId(MockHttpServletRequest request,
                                           MockHttpServletResponse response) throws Exception {
        AtomicReference<String> bound = new AtomicReference<>();
        FilterChain chain = (req, res) -> bound.set(CorrelationId.current());
        filter.doFilter(request, response, chain);
        return bound.get();
    }
}
