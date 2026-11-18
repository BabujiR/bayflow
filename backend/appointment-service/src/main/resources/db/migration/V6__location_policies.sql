-- Organizations and locations, phase L2: staff see and change only the appointments of the
-- locations they work at. Each transaction names them in app.locations ("*" = every location
-- of the organization, otherwise a comma-separated list of ids; missing = none), next to
-- app.tenant. Customers and vehicles stay organization-wide; the outbox is internal.

DROP POLICY tenant_isolation ON apt_appointments;
CREATE POLICY tenant_location_isolation ON apt_appointments
    USING (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid
           AND CASE WHEN current_setting('app.locations', true) = '*' THEN true
                    ELSE location_id = ANY (string_to_array(NULLIF(current_setting('app.locations', true), ''), ',')::uuid[])
               END)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant', true), '')::uuid
           AND CASE WHEN current_setting('app.locations', true) = '*' THEN true
                    ELSE location_id = ANY (string_to_array(NULLIF(current_setting('app.locations', true), ''), ',')::uuid[])
               END);
