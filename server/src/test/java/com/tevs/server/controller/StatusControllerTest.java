package com.tevs.server.controller;

import com.tevs.server.model.StatusMessage;
import com.tevs.server.service.NodeState;
import com.tevs.server.service.NodeStateManager;
import com.tevs.server.service.StatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StatusController.class)
class StatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StatusService statusService;

    @MockBean
    private NodeStateManager nodeStateManager;

    private StatusMessage sample() {
        return new StatusMessage("alice", "Hello", Instant.now(), 48.2, 16.4);
    }

    @Test
    void postStatusReturns201() throws Exception {
        StatusMessage msg = sample();
        when(statusService.saveOrUpdate(any())).thenReturn(
                new StatusService.SaveResult(StatusService.SaveOutcome.CREATED, msg));

        mockMvc.perform(post("/api/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"statustext\":\"Hello\",\"latitude\":48.2,\"longitude\":16.4}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void postStatusReturns200OnUpdate() throws Exception {
        StatusMessage msg = sample();
        when(statusService.saveOrUpdate(any())).thenReturn(
                new StatusService.SaveResult(StatusService.SaveOutcome.UPDATED, msg));

        mockMvc.perform(post("/api/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"statustext\":\"Hello\",\"latitude\":48.2,\"longitude\":16.4}"))
                .andExpect(status().isOk());
    }

    @Test
    void postStatusReturns400WhenMissingUsername() throws Exception {
        mockMvc.perform(post("/api/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statustext\":\"Hello\",\"latitude\":48.2,\"longitude\":16.4}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postStatusReturns400WhenLatitudeOutOfRange() throws Exception {
        mockMvc.perform(post("/api/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"statustext\":\"Hello\",\"latitude\":200,\"longitude\":16.4}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getStatusReturns200() throws Exception {
        when(statusService.findByUsername("alice")).thenReturn(Optional.of(sample()));

        mockMvc.perform(get("/api/status/alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void getStatusReturns404() throws Exception {
        when(statusService.findByUsername("nobody")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/status/nobody"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteStatusReturns204() throws Exception {
        when(statusService.deleteByUsername("alice")).thenReturn(true);

        mockMvc.perform(delete("/api/status/alice"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteStatusReturns404() throws Exception {
        when(statusService.deleteByUsername("nobody")).thenReturn(false);

        mockMvc.perform(delete("/api/status/nobody"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllStatusesReturnsList() throws Exception {
        when(statusService.findAll()).thenReturn(List.of(sample()));

        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("alice"));
    }

    @Test
    void healthReturnsUpWithNodeState() throws Exception {
        when(nodeStateManager.getState()).thenReturn(NodeState.ACTIVE);

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.nodeState").value("ACTIVE"));
    }

    @Test
    void healthShowsBootstrappingState() throws Exception {
        when(nodeStateManager.getState()).thenReturn(NodeState.BOOTSTRAPPING);

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeState").value("BOOTSTRAPPING"));
    }

    @Test
    void syncAllReturnsList() throws Exception {
        when(statusService.findAll()).thenReturn(List.of(sample()));

        mockMvc.perform(get("/api/sync/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("alice"));
    }
}
