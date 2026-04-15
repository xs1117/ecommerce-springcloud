package org.example.user.service;

import org.example.user.domain.PointsLedger;
import org.example.user.domain.UserAccount;
import org.example.user.repository.PointsLedgerRepository;
import org.example.user.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class MemberService {

    private final UserAccountRepository userAccountRepository;
    private final PointsLedgerRepository pointsLedgerRepository;

    public MemberService(UserAccountRepository userAccountRepository, PointsLedgerRepository pointsLedgerRepository) {
        this.userAccountRepository = userAccountRepository;
        this.pointsLedgerRepository = pointsLedgerRepository;
    }

    public Map<String, Object> profile(Long userId) {
        UserAccount user = load(userId);
        return Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "nickname", user.getNickname(),
                "role", user.getRole(),
                "status", user.getStatus(),
                "points", user.getPoints(),
                "memberLevel", user.getMemberLevel()
        );
    }

    public Map<String, Object> addPoints(Long userId, int delta, String reason) {
        if (delta == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "积分变动值不能为0");
        }
        UserAccount user = load(userId);
        int after = user.getPoints() + delta;
        user.setPoints(Math.max(after, 0));
        user = userAccountRepository.save(user);

        PointsLedger ledger = new PointsLedger();
        ledger.setUserId(user.getId());
        ledger.setChangePoints(delta);
        ledger.setReason(reason);
        ledger.setAfterPoints(user.getPoints());
        pointsLedgerRepository.save(ledger);

        return profile(user.getId());
    }

    private UserAccount load(Long userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
    }
}

