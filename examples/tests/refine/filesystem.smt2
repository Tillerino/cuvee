;! Cuvee -z3

(declare-sort File)
(declare-sort Address)
(declare-sort Name)

(declare-const empty File)
(declare-const null  Address)

(declare-const fs0 (Array Name File))
(assert (forall ((name Name))
  (= (select fs0 name)
     empty)))

(declare-const index0 (Array Name Address))
(assert (forall ((name Name))
  (= (select index0 name)
     null)))

(define-class
  AbstractFS
  ((fs (Array Name File)))
  (init () ()
    (assign (fs fs0)))
  (read ((name Name))
        ((file File))
    (assign (file (select fs name)))
    :precondition (distinct (select fs name) empty))
  (write ((name Name) (file File))
         ()
    (assign ((select fs name) file))
    :precondition (and (= (select fs name) empty)
                       (distinct file empty))))

(define-class
  FlashFS
  ((index (Array Name Address))
   (disk  (Array Address File)))
  (init () ()
    (assign (index index0)
            ((select disk null) empty)))
  (read ((name Name))
        ((file File))
    (assign (file (select disk (select index name))))
    :precondition (distinct (select index name) null))
  (write ((name Name) (file File))
         ()
    (local  (addr Address))
    (assume (exists ((addr Address))
      (and (distinct addr null)
           (= (select disk addr) empty))))
    (choose (addr)
      (and (distinct addr null)
           (= (select disk addr) empty)))
    (assign ((select index name) addr)
            ((select disk  addr) file))
    :precondition (and (= (select index name) null)
                       (distinct file empty))))

(push)
  (declare-fun R
    ((Array Name File)
     (Array Name Address)
     (Array Address File))
     Bool)

  (assert (forall
    ((fs    (Array Name File))
     (index (Array Name Address))
     (disk  (Array Address File)))
    (= (R fs index disk)
       (forall ((name Name)) (and
         (=> (distinct (select fs name) empty)
             (and (distinct (select index name) null)
                  (= (select fs name)
                     (select disk (select index name)))))
         (=> (= (select fs name) empty)
             (= (select index name) null)))))))

  (verify-refinement AbstractFS FlashFS R)
  (set-info :status unsat)
  (check-sat)
(pop)

(push)
  (verify-refinement AbstractFS FlashFS R :synthesize output precondition)
  (set-info :status unsat)
  (check-sat)
(pop)