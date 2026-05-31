package io.vidocq.tools.arago.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Local allowlist entry for a speaker (cf. arago-spec §4.2/§4.8). Authentication is delegated to
 * Keycloak (OIDC); <em>authorization</em> is this table, managed by the superadmin.
 *
 * <p>A speaker is invited by email (the matching key). The {@code oidcSub} is filled in on the first
 * successful OIDC login, binding the Keycloak identity to this entry. The effective role comes from
 * {@link #getRole()} here, not from Keycloak realm roles.</p>
 */
@Entity
@Table(name = "speakers")
public class Speaker {

    @Id
    private String id;

    /** Lowercased, trimmed email — invitation/OIDC-matching key (unique). */
    @Column(nullable = false, unique = true)
    private String email;

    /** OIDC {@code sub}, null until the first login; unique once set. */
    @Column(name = "oidc_sub", unique = true)
    private String oidcSub;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** {@code false} cuts access at the next token check without deleting history. */
    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "display_name")
    private String displayName;

    /** Who created the entry — traceability ("superadmin"). */
    @Column(name = "invited_by", nullable = false)
    private String invitedBy;

    @Column(name = "invited_at", nullable = false)
    private Instant invitedAt;

    @Column(name = "first_login_at")
    private Instant firstLoginAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    public Speaker() {}

    public Speaker(String id, String email, Role role, boolean enabled, String displayName,
                   String invitedBy, Instant invitedAt) {
        this.id          = id;
        this.email       = email;
        this.role        = role;
        this.enabled     = enabled;
        this.displayName = displayName;
        this.invitedBy   = invitedBy;
        this.invitedAt   = invitedAt;
    }

    public String  getId()                          { return id;             }
    public void    setId(String id)                 { this.id = id;          }
    public String  getEmail()                       { return email;          }
    public void    setEmail(String email)           { this.email = email;    }
    public String  getOidcSub()                     { return oidcSub;        }
    public void    setOidcSub(String oidcSub)       { this.oidcSub = oidcSub; }
    public Role    getRole()                        { return role;           }
    public void    setRole(Role role)               { this.role = role;      }
    public boolean isEnabled()                      { return enabled;        }
    public void    setEnabled(boolean enabled)      { this.enabled = enabled; }
    public String  getDisplayName()                 { return displayName;    }
    public void    setDisplayName(String name)      { this.displayName = name; }
    public String  getInvitedBy()                   { return invitedBy;      }
    public void    setInvitedBy(String invitedBy)   { this.invitedBy = invitedBy; }
    public Instant getInvitedAt()                   { return invitedAt;      }
    public void    setInvitedAt(Instant at)         { this.invitedAt = at;   }
    public Instant getFirstLoginAt()                { return firstLoginAt;   }
    public void    setFirstLoginAt(Instant at)      { this.firstLoginAt = at; }
    public Instant getLastSeenAt()                  { return lastSeenAt;     }
    public void    setLastSeenAt(Instant at)        { this.lastSeenAt = at;  }
}
