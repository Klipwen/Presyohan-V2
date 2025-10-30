package PresyohanBackend.service;

import PresyohanBackend.model.User;

import java.util.List;
import java.util.Optional;

public interface UserService {
    List<User> findAll();
    Optional<User> findByEmail(String email);
}