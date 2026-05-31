package io.vidocq.tools.arago.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Audit trail of superadmin actions (cf. arago-spec §4.8/§10.2). Never stores secrets, passwords or
 * attendee emails — only the acted-upon identifier and metadata.
 */
@Entity
@Table(name = "admin_audit")
public class AdminAudit {

    @Id
    private String id;

    /** Always {@code "superadmin"} for now (single root account). */
    @Column(nullable = false)
    private String actor;

    /** e.g. {@code speaker.create}, {@code room.force-end}, {@code purge.run}. */
    @Column(nullable = false)
    private String action;

    /** Id of the target (speakerId, roomId…), nullable for global actions. */
    private String target;

    /** Truncated client IP (/24 v4 or /48 v6), never the full address. */
    @Column(name = "ip_truncated")
    private String ipTruncated;

    @Column(name = "at", nullable = false)
    private Instant at;

    public AdminAudit() {}

    public AdminAudit(String id, String actor, String action, String target, String ipTruncated, Instant at) {
        this.id          = id;
        this.actor       = actor;
        this.action      = action;
        this.target      = target;
        this.ipTruncated = ipTruncated;
        this.at          = at;
    }

    public String  getId()                       { return id;          }
    public void    setId(String id)              { this.id = id;       }
    public String  getActor()                    { return actor;       }
    public void    setActor(String actor)        { this.actor = actor; }
    public String  getAction()                   { return action;      }
    public void    setAction(String action)      { this.action = action; }
    public String  getTarget()                   { return target;      }
    public void    setTarget(String target)      { this.target = target; }
    public String  getIpTruncated()              { return ipTruncated; }
    public void    setIpTruncated(String ip)     { this.ipTruncated = ip; }
    public Instant getAt()                       { return at;          }
    public void    setAt(Instant at)             { this.at = at;       }
}
