package com.swiftlabs.invoicing.security.service;

import com.swiftlabs.invoicing.security.domain.Profile;
import com.swiftlabs.invoicing.security.repository.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ProfileService {

    private final ProfileRepository profileRepository;

    public ProfileService(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    public List<Profile> findAll() {
        return profileRepository.findAll();
    }

    public Optional<Profile> findById(Long id) {
        return profileRepository.findById(id);
    }

    @Transactional
    public Profile save(Profile profile) {
        if (profileRepository.existsByName(profile.getName())
            && (profile.getId() == null || !profileRepository.findById(profile.getId())
            .map(p -> p.getName().equals(profile.getName())).orElse(false))) {
            throw new IllegalArgumentException("Ce nom de profil existe déjà.");
        }
        return profileRepository.save(profile);
    }

    @Transactional
    public void delete(Long id) {
        profileRepository.deleteById(id);
    }
}
