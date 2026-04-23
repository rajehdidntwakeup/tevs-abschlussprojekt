package com.tevs.server.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "status_messages")
public class StatusMessage {

    @Id
    private String username;

    private String statustext;
    private Instant time;
    private Double latitude;
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
