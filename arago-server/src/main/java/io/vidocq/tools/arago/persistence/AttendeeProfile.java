package io.vidocq.tools.arago.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * An attendee who joined a room and <em>optionally</em> provided an email (cf. arago-spec §4.2/§4.7).
 * Keyed by email (the cross-room identity); a pseudo-only attendee has no profile row. Consent is
 * recorded inline (the explicit GDPR checkbox version + timestamp) — a richer consent audit trail is
 * a later refinement. Anonymisation/erasure (right-to-be-forgotten) lands with the GDPR slice.
 */
@Entity
@Table(name = "attendee_profiles")
public class AttendeeProfile {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String pseudo;

    /** Version of the consent text the attendee accepted (audit trail for §4.7). */
    @Column(name = "consent_text_version")
    private String consentTextVersion;

    @Column(name = "consent_at")
    private Instant consentAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public AttendeeProfile() {}

    public AttendeeProfile(String id, String email, String pseudo, String consentTextVersion,
                           Instant consentAt, Instant createdAt) {
        this.id                 = id;
        this.email              = email;
        this.pseudo             = pseudo;
        this.consentTextVersion = consentTextVersion;
        this.consentAt          = consentAt;
        this.createdAt          = createdAt;
    }

    public String  getId()                          { return id;                 }
    public void    setId(String id)                 { this.id = id;              }
    public String  getEmail()                       { return email;              }
    public void    setEmail(String email)           { this.email = email;        }
    public String  getPseudo()                      { return pseudo;             }
    public void    setPseudo(String pseudo)         { this.pseudo = pseudo;      }
    public String  getConsentTextVersion()          { return consentTextVersion; }
    public void    setConsentTextVersion(String v)  { this.consentTextVersion = v; }
    public Instant getConsentAt()                   { return consentAt;          }
    public void    setConsentAt(Instant at)         { this.consentAt = at;       }
    public Instant getCreatedAt()                   { return createdAt;          }
    public void    setCreatedAt(Instant at)         { this.createdAt = at;       }
}
