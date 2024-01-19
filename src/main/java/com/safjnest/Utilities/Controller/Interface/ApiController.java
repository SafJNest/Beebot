package com.safjnest.Utilities.Controller.Interface;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employees")
public class ApiController {
    
    @Autowired
    private ApiService service;

    @GetMapping("/{id}")
    public ApiClass getEmployeeById(@PathVariable Integer id) {
        return new ApiClass(id, "ciao");
    }

    @PostMapping
    public ApiClass addEmployee(ApiClass employee) {
        return service.addEmployee(employee);
    }
}
