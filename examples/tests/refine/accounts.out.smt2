(set-logic ALL)
(declare-fun overdraft-limit () Int)
(push 1)
(assert (>= overdraft-limit 0))
(assert (not (and (and (forall ((b Int) (d Int) (c Int)) (=> (and (and true true) true) (and true (and true (= 0 (- 0 0)))))) (forall ((b Int) (amount Int) (new-balance Int) (d Int) (c Int) (|add'| Int) (|increased'| Int)) (=> (and (and (= amount |add'|) (> amount 0)) (= b (- c d))) (and (> |add'| 0) (and (= (+ b amount) (- (+ c |add'|) d)) (= (+ b amount) (- (+ c |add'|) d))))))) (forall ((b Int) (amount Int) (new-balance Int) (d Int) (c Int) (|remove'| Int) (|decreased'| Int)) (=> (and (and (= amount |remove'|) (and (> amount 0) (<= amount b))) (= b (- c d))) (and (and (> |remove'| 0) (<= |remove'| (+ (- c d) overdraft-limit))) (and (= (- b amount) (- c (+ d |remove'|))) (= (- b amount) (- c (+ d |remove'|))))))))))
(check-sat)
(pop 1)
