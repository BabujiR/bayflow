-- Adds the ADMIN role (account management).

ALTER TABLE auth_user_roles DROP CONSTRAINT ck_auth_user_roles_role;

ALTER TABLE auth_user_roles
    ADD CONSTRAINT ck_auth_user_roles_role
        CHECK (role IN ('CUSTOMER', 'SERVICE_ADVISOR', 'MECHANIC', 'ADMIN'));
