package com.bistrobyte.userservice.config;

import com.bistrobyte.common.security.Role;
import com.bistrobyte.userservice.domain.UserAccount;
import com.bistrobyte.userservice.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Seeds one account per role so the system can be validated end to end straight after
 * start-up. Disable with {@code bistrobyte.seed.enabled=false} outside development.
 */
@Configuration
@ConditionalOnProperty(name = "bistrobyte.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DefaultAccountsInitializer {

    private static final Logger log = LoggerFactory.getLogger(DefaultAccountsInitializer.class);

    @Bean
    public ApplicationRunner seedAccounts(UserAccountRepository repository, PasswordEncoder encoder) {
        return args -> {
            seed(repository, encoder, "admin", "admin@bistrobyte.com", "Ava Administrator", Role.ADMIN);
            seed(repository, encoder, "staff", "staff@bistrobyte.com", "Sam Server", Role.STAFF);
            seed(repository, encoder, "kitchen", "kitchen@bistrobyte.com", "Kai Kitchen", Role.KITCHEN);
            seed(repository, encoder, "customer", "customer@bistrobyte.com", "Casey Customer", Role.CUSTOMER);
        };
    }

    private void seed(UserAccountRepository repository, PasswordEncoder encoder,
                      String username, String email, String fullName, Role role) {
        if (repository.existsByUsernameIgnoreCase(username)) {
            return;
        }
        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setEmail(email);
        account.setFullName(fullName);
        account.setPasswordHash(encoder.encode("Password@123"));
        account.setRole(role);
        account.setActive(true);
        repository.save(account);
        log.info("Seeded {} account '{}' (password: Password@123)", role, username);
    }
}
