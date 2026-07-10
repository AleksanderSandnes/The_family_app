// Behaviour tests for MealViewModel via MockRepository + NoopRealtimeObserver.
@testable import FamilyApp
import XCTest

@MainActor
final class MealViewModelTests: XCTestCase {
    private func makeMock(userId: String = "u1", familyId: String? = "f1") -> MockRepository {
        let mock = MockRepository()
        mock.session.signIn(userId: userId)
        var user = UserModel()
        user.id = userId
        user.familyId = familyId
        mock.users[userId] = user
        return mock
    }

    private func makeVM(_ mock: MockRepository) -> MealViewModel {
        MealViewModel(repo: mock, realtime: { NoopRealtimeObserver() })
    }

    func testLoadPlansFromRepo() async {
        let mock = makeMock()
        var plan = MealPlanModel()
        plan.id = "p1"
        plan.name = "Week 28"
        plan.familyId = "f1"
        mock.mealPlansResult = [plan]
        let vm = makeVM(mock)
        await waitUntil { vm.plans.contains { $0.name == "Week 28" } }
        XCTAssertTrue(vm.plans.contains { $0.id == "p1" })
    }

    func testCreatePlanInsertsPlanWithCorrectFieldsAndDayRows() async {
        let mock = makeMock()
        var realPlan = MealPlanModel()
        realPlan.id = "real-plan"
        mock.insertMealPlanResult = realPlan
        let vm = makeVM(mock)
        await waitUntil { true }

        let fromIso = "2026-07-06"
        let toIso = "2026-07-08"
        vm.createPlan(name: "Summer", fromIso: fromIso, toIso: toIso, icon: "beach", color: 0x6366F1)

        await waitUntil { !mock.insertedMealPlans.isEmpty }
        let inserted = mock.insertedMealPlans.first
        XCTAssertEqual(inserted?.name, "Summer")
        XCTAssertEqual(inserted?.familyId, "f1")
        XCTAssertEqual(inserted?.icon, "beach")
        XCTAssertEqual(inserted?.color, 0x6366F1)
        XCTAssertEqual(inserted?.fromDate, fromIso)
        XCTAssertEqual(inserted?.toDate, toIso)
        let expectedWeek = isoWeekNumber(of: LocalDate(iso: fromIso)!)
        XCTAssertEqual(inserted?.week, expectedWeek)

        // One day row per date in [from, to], keyed on the persisted plan id.
        await waitUntil { mock.insertedMealPlanDays.count == 3 }
        XCTAssertEqual(mock.insertedMealPlanDays.count, 3)
        XCTAssertTrue(mock.insertedMealPlanDays.allSatisfy { $0.mealPlanId == "real-plan" })
        XCTAssertEqual(mock.insertedMealPlanDays.map(\.date), [fromIso, "2026-07-07", toIso])
    }

    func testRenamePlanCallsRepoAndUpdatesOptimistically() async {
        let mock = makeMock()
        var plan = MealPlanModel()
        plan.id = "p1"
        plan.name = "Old"
        let vm = makeVM(mock)
        await waitUntil { true }

        vm.renamePlan(plan, newName: "New")
        await waitUntil { !mock.renamedMealPlans.isEmpty }
        XCTAssertEqual(mock.renamedMealPlans.first?.id, "p1")
        XCTAssertEqual(mock.renamedMealPlans.first?.name, "New")
        XCTAssertEqual(vm.selectedPlan?.name, "New")
    }

    func testSetPlanIconCallsRepo() async {
        let mock = makeMock()
        var plan = MealPlanModel()
        plan.id = "p1"
        let vm = makeVM(mock)
        await waitUntil { true }

        vm.setPlanIcon(plan, newIcon: "pizza")
        await waitUntil { !mock.mealPlanIcons.isEmpty }
        XCTAssertEqual(mock.mealPlanIcons.first?.id, "p1")
        XCTAssertEqual(mock.mealPlanIcons.first?.icon, "pizza")
        XCTAssertEqual(vm.selectedPlan?.icon, "pizza")
    }

    func testSetPlanColorCallsRepo() async {
        let mock = makeMock()
        var plan = MealPlanModel()
        plan.id = "p1"
        let vm = makeVM(mock)
        await waitUntil { true }

        vm.setPlanColor(plan, color: 0xEC4899)
        await waitUntil { !mock.mealPlanColors.isEmpty }
        XCTAssertEqual(mock.mealPlanColors.first?.id, "p1")
        XCTAssertEqual(mock.mealPlanColors.first?.color, 0xEC4899)
        XCTAssertEqual(vm.selectedPlan?.color, 0xEC4899)
    }

    func testDeletePlanRemovesLocallyAndCallsRepo() async {
        let mock = makeMock()
        var plan = MealPlanModel()
        plan.id = "gone"
        plan.name = "Gone"
        plan.familyId = "f1"
        mock.mealPlansResult = [plan]
        let vm = makeVM(mock)
        await waitUntil { vm.plans.contains { $0.id == "gone" } }

        // After the delete, the server returns no plans.
        mock.mealPlansResult = []
        vm.deletePlan(plan)
        await waitUntil { !mock.deletedMealPlanIds.isEmpty }
        XCTAssertEqual(mock.deletedMealPlanIds.first, "gone")
        await waitUntil { !vm.plans.contains { $0.id == "gone" } }
        XCTAssertFalse(vm.plans.contains { $0.id == "gone" })
    }

    func testLoadPlanDetailSetsSelectedPlanAndSortedDays() async {
        let mock = makeMock()
        var plan = MealPlanModel()
        plan.id = "p1"
        plan.name = "Detail"
        mock.mealPlanDetailResult = [plan]
        var dayB = MealPlanDayModel()
        dayB.id = "d2"
        dayB.date = "2026-07-08"
        var dayA = MealPlanDayModel()
        dayA.id = "d1"
        dayA.date = "2026-07-06"
        mock.mealPlanDaysForPlanResult = [dayB, dayA]
        let vm = makeVM(mock)
        await waitUntil { true }

        vm.loadPlanDetail("p1")
        await waitUntil { vm.days.count == 2 }
        XCTAssertEqual(vm.selectedPlan?.name, "Detail")
        XCTAssertEqual(vm.days.map(\.id), ["d1", "d2"])
    }

    func testSetFoodUpdatesOptimisticallyAndCallsRepo() async {
        let mock = makeMock()
        var plan = MealPlanModel()
        plan.id = "p1"
        mock.mealPlanDetailResult = [plan]
        var day = MealPlanDayModel()
        day.id = "d1"
        day.mealPlanId = "p1"
        day.date = "2026-07-06"
        mock.mealPlanDaysForPlanResult = [day]
        let vm = makeVM(mock)
        await waitUntil { true }
        vm.loadPlanDetail("p1")
        await waitUntil { vm.days.contains { $0.id == "d1" } }

        vm.setFood(day, food: "Tacos")
        await waitUntil { !mock.mealDayFoods.isEmpty }
        XCTAssertEqual(mock.mealDayFoods.first?.id, "d1")
        XCTAssertEqual(mock.mealDayFoods.first?.food, "Tacos")
    }
}
