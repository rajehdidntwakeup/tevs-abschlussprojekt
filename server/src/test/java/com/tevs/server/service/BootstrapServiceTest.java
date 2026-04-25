package com.tevs.server.service;

import com.tevs.server.model.StatusMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BootstrapServiceTest {

    @Mock
    private StatusService statusService;

    @Mock
    private NodeStateManager nodeStateManager;

    private RestTemplateBuilder mockBuilder() {
        RestTemplateBuilder builder = mock(RestTemplateBuilder.class);
        when(builder.rootUri("")).thenReturn(builder);
        when(builder.setConnectTimeout(any())).thenReturn(builder);
        when(builder.setReadTimeout(any())).thenReturn(builder);
        when(builder.build()).thenReturn(mock(RestTemplate.class));
        return builder;
    }

    @Test
    void transitionsToActiveWhenNoPeers() {
        BootstrapService service = new BootstrapService(statusService, nodeStateManager,
                mockBuilder(), "");

        service.bootstrap();

        verify(nodeStateManager).setState(NodeState.ACTIVE);
        verify(statusService, never()).saveOrUpdate(any());
    }

    @Test
    void transitionsToActiveWhenPeersBlank() {
        BootstrapService service = new BootstrapService(statusService, nodeStateManager,
                mockBuilder(), "   ");

        service.bootstrap();

        verify(nodeStateManager).setState(NodeState.ACTIVE);
        verify(statusService, never()).saveOrUpdate(any());
    }

    @Test
    void handlesPeerFailureGracefully() {
        RestTemplateBuilder builder = mock(RestTemplateBuilder.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(builder.rootUri("")).thenReturn(builder);
        when(builder.setConnectTimeout(any())).thenReturn(builder);
        when(builder.setReadTimeout(any())).thenReturn(builder);
        when(builder.build()).thenReturn(restTemplate);
        when(restTemplate.exchange(
                anyString(),
                any(HttpMethod.class),
                any(),
                any(ParameterizedTypeReference.class)
        )).thenThrow(new RuntimeException("Connection refused"));

        BootstrapService service = new BootstrapService(statusService, nodeStateManager,
                builder, "http://peer:8080");

        service.bootstrap();

        verify(nodeStateManager).setState(NodeState.ACTIVE);
    }
}
