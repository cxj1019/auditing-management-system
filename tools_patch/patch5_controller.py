import io
p = r'backend/src/main/java/com/accounting/firm/schedule/controller/ScheduleController.java'
s = io.open(p, encoding='utf-8').read()

if 'hours-matrix' not in s:
    s = s.replace(
"""    @GetMapping("/hours-summary")""",
"""    /** 人 × 项目 工时矩阵（部门范围内） */
    @PreAuthorize("hasAuthority('business:schedule:hours')")
    @GetMapping("/hours-matrix")
    public ApiResult<List<java.util.Map<String, Object>>> hoursMatrix(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResult.success(scheduleService.hoursMatrix(startDate, endDate));
    }

    /** 经理确认成员时段工时 */
    @PreAuthorize("hasAuthority('business:schedule:hours')")
    @PostMapping("/confirm")
    public ApiResult<Integer> confirm(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                      @RequestParam(required = false) Long userId,
                                      @AuthenticationPrincipal SecurityUser currentUser) {
        return ApiResult.success(scheduleService.confirmHours(startDate, endDate, userId, currentUser));
    }

    /** 已锁定月份清单 */
    @GetMapping("/locks")
    public ApiResult<List<String>> locks() {
        return ApiResult.success(scheduleService.listLocks());
    }

    /** 锁定/解锁月份（仅管理员） */
    @PostMapping("/locks")
    public ApiResult<Void> lockMonth(@RequestParam String month,
                                     @AuthenticationPrincipal SecurityUser currentUser) {
        if (!currentUser.hasRole("admin")) {
            return ApiResult.error("仅管理员可锁定月份");
        }
        scheduleService.lockMonth(month, currentUser);
        return ApiResult.success();
    }

    @DeleteMapping("/locks")
    public ApiResult<Void> unlockMonth(@RequestParam String month,
                                       @AuthenticationPrincipal SecurityUser currentUser) {
        if (!currentUser.hasRole("admin")) {
            return ApiResult.error("仅管理员可解锁月份");
        }
        scheduleService.unlockMonth(month, currentUser);
        return ApiResult.success();
    }

    @GetMapping("/hours-summary")""")
    io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('schedule controller ok')
