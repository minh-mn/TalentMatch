package com.talentmatch.model;
import jakarta.persistence.*;
@Entity @Table(name="community_votes",uniqueConstraints=@UniqueConstraint(columnNames={"question_id","user_id"}))
public class CommunityVote {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @ManyToOne(optional=false) @JoinColumn(name="question_id") public CommunityQuestion question;
 @ManyToOne(optional=false) @JoinColumn(name="user_id") public User user;
 public int value=1;
}
