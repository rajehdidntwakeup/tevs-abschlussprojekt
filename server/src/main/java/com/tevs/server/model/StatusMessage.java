package com.tevs.server.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.*;
import java.time.Instant;

@Entity
@Table(name = "status_messages")
public class StatusMessage {

    @Id
    @NotBlank(message = "username is required")
    private String username;

    @NotBlank(message = "statustext is required")
    private String statustext;

    private Instant time;

    @NotNull(message = "latitude is required")
    @Min(value = -90, message = "latitude must be at least -90")
    @Max(value = 90, message = "latitude must be at most 90")
    private Double latitude;

    @NotNull(message = "longitude is required")
    @Min(value = -180, message = "longitude must be at least -180")
    @Max(value = 180, message = "longitude must be at most 180")
    private Double longitude;

    public StatusMessage() {}

    public StatusMessage(String username, String statustext, Instant time, Double latitude, Double longitude) {
        this.username = username;
        this.statustext = statustext;
        this.time = time;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getStatustext() { return statustext; }
    public void setStatustext(String statustext) { this.statustext = statustext; }

    public Instant getTime() { return time; }
    public void setTime(Instant time) { this.time = time; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
}
