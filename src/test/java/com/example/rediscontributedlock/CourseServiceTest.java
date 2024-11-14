package com.example.rediscontributedlock;

import com.example.rediscontributedlock.entity.Course;
import com.example.rediscontributedlock.entity.EnrollmentRecord;
import com.example.rediscontributedlock.entity.Student;
import com.example.rediscontributedlock.repository.CourseRepository;
import com.example.rediscontributedlock.repository.EnrollmentRecordRepository;
import com.example.rediscontributedlock.repository.StudentRepository;
import com.example.rediscontributedlock.service.CourseService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CourseServiceTest {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentRecordRepository enrollmentRecordRepository;

    @Autowired
    private CourseService courseService;

    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private StringRedisTemplate redisTemplate;

    private List<Course> courses;


    @Test
    public void testConcurrentEnrollment() throws InterruptedException {
        List<Student> students = studentRepository.findAll(); // 模擬載入500位學生
        List<Course> courses = courseRepository.findAll(); // 模擬3門課程

        // 假設學生數量為500，每門課程各有40個座位
        ExecutorService executor = Executors.newFixedThreadPool(50);
        List<Future<Boolean>> results = new ArrayList<>();

        for (Student student : students) {
            // 隨機選擇一門課程和座位
            Course randomCourse = courses.get(ThreadLocalRandom.current().nextInt(courses.size()));
            String seat = "seat:" + ThreadLocalRandom.current().nextInt(1, 6);

            // 提交選課任務
            results.add(executor.submit(() ->
                courseService.enrollCourseV1(student.getStudentId(), randomCourse.getCourseId(), seat)
            ));
        }

        // 計算成功選課和未成功選課的人數
        long successfulEnrollments = results.stream()
            .map(future -> {
                try {
                    return future.get();  // 這裡會阻塞，直到取得結果
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;  // 若發生例外，視為失敗
                }
            })
            .filter(result -> result)  // 篩選成功選課的結果
            .count();

        long unsuccessfulEnrollments = students.size() - successfulEnrollments;

        // 斷言每門課程的座位是否已滿，並驗證選課記錄的一致性
        courses.forEach(course -> {
            Course updatedCourse = courseRepository.findById(course.getCourseId()).orElseThrow();
            Assertions.assertTrue(updatedCourse.getAvailableSeats() >= 0, "可用座位數不應為負");
            Assertions.assertEquals(5 - updatedCourse.getAvailableSeats(),
                enrollmentRecordRepository.countByCourseId(course.getCourseId()),
                "課程座位記錄與選課記錄數量不一致");
        });

        // 輸出結果
        System.out.println("成功選到課程的學生人數: " + successfulEnrollments);
        System.out.println("未成功選到課程的學生人數: " + unsuccessfulEnrollments);

        executor.shutdown();
    }

    @Test
    public void initializeRedisData() {
        enrollmentRecordRepository.deleteAll();
        // 使用通配符查找所有前綴為 'course' 的鍵
        Set<String> keys = redisTemplate.keys("course*");

        if (keys != null && !keys.isEmpty()) {
            // 刪除找到的所有鍵
            redisTemplate.delete(keys);
            System.out.println("成功刪除所有前綴為 'course' 的資料");
        } else {
            System.out.println("未找到任何前綴為 'course' 的資料");
        }
        List<Course> courses = courseRepository.findAll();
        HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

        for (Course course : courses) {
            // 使用明確的前綴來區分數據鍵和鎖鍵
            String courseKey = "course:data:" + course.getCourseId();

            for (int i = 1; i <= course.getAvailableSeats(); i++) { // 假設每門課程有40個座位
                String seat = "seat:" + i;
                hashOps.putIfAbsent(courseKey, seat, "available"); // 初始化座位鍵值
            }
        }
    }



    @Test
    public void testFindCourseValue() {
        // 定義要查找的鍵和字段
        String courseKey = "course:1";
        String seatKey = "seat:1";
        String expectedValue = "available";

        // 確保 Redis 中的 Hash 結構中包含此鍵值對（用於測試準備）
        HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        hashOps.put(courseKey, seatKey, expectedValue);

        // 從 Redis 中獲取 Hash 結構中的指定字段的值
        String actualValue = hashOps.get(courseKey, seatKey);

        // 斷言檢查
        Assertions.assertNotNull(actualValue, "Redis 中找不到指定的 Hash 鍵或字段");
        Assertions.assertEquals(expectedValue, actualValue, "Redis 中的 Hash 值不匹配");

        System.out.println("找到的 course:1 的 seat:1 值為: " + actualValue);
    }


}
