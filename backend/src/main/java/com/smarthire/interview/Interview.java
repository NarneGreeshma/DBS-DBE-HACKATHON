package com.smarthire.interview;

import com.smarthire.application.Application;
import com.smarthire.common.BaseEntity;
import com.smarthire.enums.Enums.InterviewMode;
import com.smarthire.enums.Enums.InterviewStatus;
import com.smarthire.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "interviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Interview extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheduled_by")
    private User scheduledBy;

    @Column(name = "round", nullable = false)
    @Builder.Default
    private Integer round = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 24)
    @Builder.Default
    private InterviewMode mode = InterviewMode.ONLINE;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "duration_minutes", nullable = false)
    @Builder.Default
    private Integer durationMinutes = 45;

    @Column(name = "meeting_link", length = 500)
    private String meetingLink;

    @Column(name = "location", length = 255)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    @Builder.Default
    private InterviewStatus status = InterviewStatus.SCHEDULED;

    @Column(name = "feedback")
    private String feedback;

    @Column(name = "rating")
    private Short rating;
}
