package com.safjnest.Utilities.Controller.Interface;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ApiService {

    @Autowired
    private Api repository;

    public List<ApiClass> getEmployees() {
        return repository.findAll();
    }

    public Optional<ApiClass> getEmployeeById(int id) {
        return repository.findById(id);
    }

    public ApiClass addEmployee(ApiClass employee) {
        return repository.save(employee);
    }
}