package com.safjnest.spring.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.safjnest.spring.entity.TagEntity;

@Repository
public interface TagRepository extends JpaRepository<TagEntity, Long> {
    
    Optional<TagEntity> findByName(String name);
    
    boolean existsByName(String name);
}