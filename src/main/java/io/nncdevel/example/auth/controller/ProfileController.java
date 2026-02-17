package io.nncdevel.example.auth.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ProfileController {

    private final PublicKeyCredentialUserEntityRepository userEntityRepository;
    private final UserCredentialRepository credentialRepository;

    public ProfileController(PublicKeyCredentialUserEntityRepository userEntityRepository,
                             UserCredentialRepository credentialRepository) {
        this.userEntityRepository = userEntityRepository;
        this.credentialRepository = credentialRepository;
    }

    @GetMapping("/profile")
    public String profile(Authentication authentication, Model model) {
        String username = authentication.getName();
        model.addAttribute("username", username);

        var userEntity = userEntityRepository.findByUsername(username);
        if (userEntity != null) {
            List<CredentialRecord> passkeys = credentialRepository.findByUserId(userEntity.getId());
            model.addAttribute("passkeys", passkeys);
        } else {
            model.addAttribute("passkeys", List.of());
        }
        return "profile";
    }

    @PostMapping("/profile/passkeys/delete")
    public String deletePasskey(@RequestParam String credentialId) {
        credentialRepository.delete(Bytes.fromBase64(credentialId));
        return "redirect:/profile";
    }
}
