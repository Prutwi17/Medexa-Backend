package com.medexa.security;

import com.medexa.user.AccountStatus;
import com.medexa.user.User;
import com.medexa.user.UserRole;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
public class MedexaUserPrincipal implements UserDetails {

    private final Long userId;
    private final String email;
    private final String fullName;
    private final String passwordHash;
    private final UserRole role;
    private final AccountStatus accountStatus;
    private final boolean emailVerified;
    private final Collection<? extends GrantedAuthority> authorities;

    public MedexaUserPrincipal(User user) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.fullName = user.getFullName();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
        this.accountStatus = user.getAccountStatus();
        this.emailVerified = user.isEmailVerified();
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountStatus != AccountStatus.SUSPENDED && accountStatus != AccountStatus.REJECTED;
    }

    @Override
    public boolean isEnabled() {
        return accountStatus == AccountStatus.ACTIVE;
    }
}
