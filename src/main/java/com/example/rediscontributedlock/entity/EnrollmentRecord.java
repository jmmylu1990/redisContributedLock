package com.example.rediscontributedlock.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
public @Data class EnrollmentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long recordId;
    private Long studentId;
    private Long courseId;
    private String seat;  // 用於記錄選擇的具體座位
    private LocalDateTime enrollmentTime;
}
