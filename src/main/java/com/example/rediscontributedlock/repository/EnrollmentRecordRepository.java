package com.example.rediscontributedlock.repository;

import com.example.rediscontributedlock.entity.EnrollmentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentRecordRepository extends JpaRepository<EnrollmentRecord, Long> {
    // Add method to count enrollments by course ID
    long countByCourseId(Long courseId);

    boolean existsByStudentIdAndCourseId(Long studentId, long courseId);
}
