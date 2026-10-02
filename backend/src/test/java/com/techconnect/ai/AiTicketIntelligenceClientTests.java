package com.techconnect.ai;

import com.techconnect.client.AiTicketIntelligenceClient;
import com.techconnect.dto.AiAnalysisRequest;
import com.techconnect.dto.AiAnalysisResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AiTicketIntelligenceClientTests {

    @Test
    @DisplayName("AiTicketIntelligenceClient catches connection refused and returns graceful unavailable response")
    void testClient_ConnectionRefused_ReturnsGracefulUnavailable() {
        // Pointing to a dead port where no service is listening
        AiTicketIntelligenceClient client = new AiTicketIntelligenceClient("http://localhost:59999", 500);

        AiAnalysisRequest request = AiAnalysisRequest.builder()
                .title("Network connection lost")
                .description("Cannot connect to office Wi-Fi network from conference room.")
                .build();

        AiAnalysisResponse response = client.analyzeTicket(request);

        assertThat(response).isNotNull();
        assertThat(response.isAiAvailable()).isFalse();
        assertThat(response.getMessage()).contains("unreachable or timed out");
        assertThat(response.getCategory()).isNull();
    }
}
