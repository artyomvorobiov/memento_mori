import PhotosUI
import SwiftUI

/// Отметить цель выполненной — или отредактировать воспоминание о ней.
struct CompleteGoalSheet: View {
    let goal: Goal
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var date: Date
    @State private var reflection: String
    @State private var pickerItem: PhotosPickerItem?
    @State private var newPhotoData: Data?
    @State private var newPhotoPreview: UIImage?
    @State private var removeExistingPhoto = false
    @State private var isLoadingPhoto = false

    init(goal: Goal) {
        self.goal = goal
        _date = State(initialValue: goal.completedAt ?? Date())
        _reflection = State(initialValue: goal.reflection)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(goal.isCompleted ? "Воспоминание" : "Одной мечтой меньше — одной историей больше")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Theme.accent)
                        Text(goal.title)
                            .font(.system(.title3, design: .serif))
                    }
                    .padding(.vertical, 4)
                }

                Section {
                    DatePicker("Когда", selection: $date, in: ...Date(), displayedComponents: .date)
                }

                Section("На память") {
                    TextField("Каким было это мгновение?", text: $reflection, axis: .vertical)
                        .lineLimit(3...10)
                }

                Section {
                    photoPreview
                    PhotosPicker(selection: $pickerItem, matching: .images) {
                        Label(hasPhoto ? "Выбрать другое фото" : "Добавить фото", systemImage: "photo")
                    }
                    if hasPhoto {
                        Button("Убрать фото", role: .destructive) {
                            pickerItem = nil
                            newPhotoData = nil
                            newPhotoPreview = nil
                            removeExistingPhoto = true
                        }
                    }
                } footer: {
                    Text("Фото хранится только на этом устройстве.")
                }
            }
            .navigationTitle(goal.isCompleted ? "Воспоминание" : "Выполнено")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Отмена") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Сохранить", action: save)
                        .disabled(isLoadingPhoto)
                }
            }
            .onChange(of: pickerItem) { _, item in
                loadPhoto(item)
            }
        }
    }

    private var existingPhoto: UIImage? {
        removeExistingPhoto ? nil : model.photo(for: goal)
    }

    private var hasPhoto: Bool {
        newPhotoPreview != nil || existingPhoto != nil
    }

    @ViewBuilder
    private var photoPreview: some View {
        if isLoadingPhoto {
            HStack {
                Spacer()
                ProgressView()
                Spacer()
            }
            .padding(.vertical, 24)
        } else if let image = newPhotoPreview ?? existingPhoto {
            Image(uiImage: image)
                .resizable()
                .scaledToFit()
                .frame(maxWidth: .infinity, maxHeight: 220)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
    }

    private func loadPhoto(_ item: PhotosPickerItem?) {
        guard let item else { return }
        isLoadingPhoto = true
        Task {
            let data = try? await item.loadTransferable(type: Data.self)
            let preview = data.flatMap { UIImage(data: $0) }?.mementoResized(maxDimension: 900)
            await MainActor.run {
                if let data, let preview {
                    newPhotoData = data
                    newPhotoPreview = preview
                    removeExistingPhoto = false
                }
                isLoadingPhoto = false
            }
        }
    }

    private func save() {
        let change: PhotoChange
        if let newPhotoData {
            change = .replace(newPhotoData)
        } else if removeExistingPhoto {
            change = .remove
        } else {
            change = .keep
        }
        model.saveCompletion(
            goalID: goal.id,
            date: date,
            reflection: reflection.trimmingCharacters(in: .whitespacesAndNewlines),
            photo: change
        )
        dismiss()
    }
}
